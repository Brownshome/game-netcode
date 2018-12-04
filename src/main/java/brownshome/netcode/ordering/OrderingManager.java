package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class ensures that packets are not received out of order. Packets are reconstructed by the receiving subsystem
 * and deposited here. This object then handles dispatching the packets in order to any packet handlers.
 */
public class OrderingManager {
	/*
	 * Care must be taken to ensure that integer wraparound does not break the ordering of the priority queue, when a packet
	 * is added, an additional check is performed to make sure that it is not both smaller than or equal to the smallest
	 * item, and larger than the largest item. If this is the case, then the ordering is no longer valid, and a NetworkException
	 * must be thrown.
	 *
	 * To achieve this a field tracks the largest packet sequence number currently in the queue.
	 */

	private final Consumer<Packet> execute, drop;

	/** Store a list of packets that have not yet been handled */
	private final Map<PacketType, PacketTypeQueue> processingQueues;

	private final Map<Packet, SequencedPacket> sequencedPacketMap = new HashMap<>();

	private Integer trim = null;
	private SequencedPacket mostRecentPacket = null;

	/**
	 * Constructs and ordering manager
	 * @param execute Code to execute a packet. This method must call notifyExecutionFinished once the packet has full executed.
	 *                If this method is not called then there will be a memory leak.
	 * @param drop Code to drop a packet
	 */
	public OrderingManager(Consumer<Packet> execute, Consumer<Packet> drop) {
		this.drop = drop;
		this.execute = execute;

		processingQueues = new HashMap<>();
	}

	/**
	 * Notifies the sorting system that a packet exists, and should have it order respected. Only reliable packets must be
	 * passed to this method, as this may cause packets to wait for this packet to be received.
	 * @param packet
	 */
	public synchronized void preDeliverPacket(SequencedPacket packet) {
		if(!checkSequenceNumber(packet)) {
			throw new IllegalArgumentException("The packet sequence number is not valid.");
		}

		var type = packet.packetType();
		getQueue(type).preReceive(packet);
	}

	/**
	 * Delivers a packet to the sorting system.
	 *
	 * While unreliable packets can be delivered in any order, reliable packets MUST be delivered before any packet that
	 * should not overtake them.
	 *
	 * If a reliable packet arrives later that should have been ordered after a packet that has already been delivered then
	 * undefined behaviour will occur.
	 *
	 * @param packet is used to order packets. For and pair of numbers A and B the ordering is defined using
	 *               A - B compared to 0, with later sequence numbers coming later. This avoids overflow issues
	 *               as long as the numbers are not more than Integer.MAX_VALUE apart.
	 *
	 * @throws IllegalArgumentException if the packet cannot be added due to the sequence numbers no longer forming a proper
	 *                                  ordering. If this occurs it means that the execution of a packet has most likely
	 *                                  hung, or failed silently.
	 **/
	public synchronized void deliverPacket(SequencedPacket packet) {
		//1. check sequence number
		if(!checkSequenceNumber(packet)) {
			throw new IllegalArgumentException("The packet sequence number is not valid.");
		}

		var type = packet.packetType();

		//2. place onto the queue
		getQueue(type).add(packet);
	}

	/** Returns true if the number is valid */
	private boolean checkSequenceNumber(SequencedPacket packet) {
		if(mostRecentPacket == null || mostRecentPacket.compareTo(packet) < 0) {
			mostRecentPacket = packet;
		}

		//The number is invalid if it is smaller than or equal to the lowest number and larger than or equal to the largest number

		SequencedPacket smallestPacket = null;

		for(var value : processingQueues.values()) {
			SequencedPacket localSmallest = value.oldestPacket();

			if(localSmallest != null &&
					(smallestPacket == null || smallestPacket.compareTo(localSmallest) > 0)) {
				smallestPacket = localSmallest;
			}
		}

		if(smallestPacket == null) {
			return true;
		}

		if(packet.compareTo(smallestPacket) <= 0 && packet.compareTo(mostRecentPacket) >= 0) {
			return false; //INVALID
		}

		return true;
	}

	/**
	 * Informs the ordering manager that a packet was executed successfully.
	 * @param packet The packet that was completed
	 */
	public void notifyExecutionFinished(Packet packet) {
		var type = new PacketType(packet);

		var queue = getQueue(type);

		var sequencedPacket = sequencedPacketMap.remove(packet);

		queue.notifyExecutionFinished(sequencedPacket);
	}



	/**
	 * This call guarantees that no packet with a sequence number smaller that this will arrive, until the sequence
	 * wraps around again.
	 **/
	public void trimPacketNumbers(int packet) {
		trim = packet;

		for(PacketTypeQueue queue : processingQueues.values()) {
			queue.trim(packet);
		}
	}

	// ******************** METHODS CALLED BY QUEUES ************************

	void executePacket(SequencedPacket packet) {
		sequencedPacketMap.put(packet.packet(), packet);
		execute.accept(packet.packet());
	}

	void dropPacket(SequencedPacket packet) {
		drop.accept(packet.packet());
	}

	PacketTypeQueue getQueue(PacketType type) {
		return processingQueues.computeIfAbsent(type, (t) -> new PacketTypeQueue(this));
	}

	/** Returns a sequence number that no received packet can be older than, if this is null then there is no limit. */
	Integer trim() {
		return trim;
	}
}
