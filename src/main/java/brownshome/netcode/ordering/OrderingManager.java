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
	public static final int MAXIMUM_OUTSTANDING_PACKETS = 1024;

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
	private final Map<Class<? extends Packet>, PacketTypeQueue> processingQueues;

	private final Map<Packet, SequencedPacket> sequencedPacketMap = new HashMap<>();

	private Integer trim = null;
	private SequencedPacket mostRecentPacket = null;

	/** This is a list of all packets that are RECEIVED or PROCESSING */
	private int packetsWaiting = 0;

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
	 * Notifies the sorting system that a packet exists, and should have its order respected. Only reliable packets must be
	 * passed to this method, as this may cause packets to wait for this packet to be received.
	 * @param packet the packet to pre-deliver
	 */
	public synchronized void preDeliverPacket(SequencedPacket packet) {
		checkSequenceNumber(packet);
		getQueue(packet.packet().getClass()).preReceive(packet);
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
	 * @param packet is used to order packets. For a pair of numbers A and B the ordering is defined using
	 *               A - B compared to 0, with later sequence numbers coming later. This avoids overflow issues
	 *               as long as the numbers are not more than Integer.MAX_VALUE apart.
	 *
	 * @throws InvalidSequenceNumberException if the packet cannot be added due to the sequence numbers no longer forming a proper
	 *                                  ordering. If this occurs it means that the execution of a packet has most likely
	 *                                  hung, or failed silently.
	 *
	 * @throws ConnectionOverloadedException if the packet cannot be added due to this connection having too many outstanding packets in the queue.
	 **/
	public synchronized void deliverPacket(SequencedPacket packet) {
		// 1. check sequence number
		checkSequenceNumber(packet);

		if (packetsWaiting >= MAXIMUM_OUTSTANDING_PACKETS) {
			throw new ConnectionOverloadedException("Connection overloaded, there are too many packets being executed.");
		}

		packetsWaiting++;
		var type = packet.packet().getClass();

		// 2. place onto the queue
		getQueue(type).add(packet);
	}

	/**
	 * Throws {@link InvalidSequenceNumberException} if the packet cannot fit in the current sequence
	 * @param packet the packet to test
	 * @throws InvalidSequenceNumberException if the sequence number is invalid
	 */
	private void checkSequenceNumber(SequencedPacket packet) {
		if (mostRecentPacket == null || mostRecentPacket.compareTo(packet) < 0) {
			mostRecentPacket = packet;
		}

		// The number is invalid if it is smaller than or equal to the lowest number and larger than or equal to the largest number

		SequencedPacket smallestPacket = null;

		for (var value : processingQueues.values()) {
			SequencedPacket localSmallest = value.oldestPacket();

			if (localSmallest != null &&
					(smallestPacket == null || smallestPacket.compareTo(localSmallest) > 0)) {
				smallestPacket = localSmallest;
			}
		}

		if (smallestPacket != null && packet.compareTo(smallestPacket) <= 0 && packet.compareTo(mostRecentPacket) >= 0) {
			throw new InvalidSequenceNumberException("The packet sequence number is not valid.");
		}
	}

	/**
	 * Informs the ordering manager that a packet was executed successfully.
	 * @param packet The packet that was completed
	 */
	public void notifyExecutionFinished(Packet packet) {
		assert packetsWaiting > 0;

		packetsWaiting--;

		var sequencedPacket = sequencedPacketMap.remove(packet);
		getQueue(packet.getClass()).notifyExecutionFinished(sequencedPacket);
	}

	/**
	 * This call guarantees that no packet with a sequence number smaller that this will arrive, until the sequence
	 * wraps around again.
	 **/
	public void trimPacketNumbers(int packet) {
		trim = packet;

		for (PacketTypeQueue queue : processingQueues.values()) {
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

	PacketTypeQueue getQueue(Class<? extends Packet> type) {
		return processingQueues.computeIfAbsent(type, (t) -> new PacketTypeQueue(this));
	}

	/** Returns a sequence number that no received packet can be older than, if this is null then there is no limit. */
	Integer trim() {
		return trim;
	}
}
