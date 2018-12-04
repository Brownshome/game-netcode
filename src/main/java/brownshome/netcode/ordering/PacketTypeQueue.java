package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

import java.util.*;

/**
 * This class stores packets and processing data for one type of packets.
 *
 * Packets pass through 4 states
 *
 * -> PRE-RECEIVE -> RECEIVED -> PROCESSING -> DONE ->
 *
 * PRE-RECEIVE, RECEIVED and PROCESSING states keep track of the oldest packet in them, as this packet would stop other
 * ones from being sent to stop unnecessary drops.
 *
 * PROCESSING and DONE states keep track of the youngest packet in them, as this packet may cause packets to be unable to
 * be sent to stop them violating the rules.
 *
 * Packets enter the PRE_RECEIVE state via a method.
 * Packets enter the RECEIVED state in any order via 'add'
 * Packets enter processing in the order of earliest sequence number first, when they move into RECEIVED, or when a packet they
 * are waiting for enters DONE.
 * Packets enter DONE in any order
 *
 * Packets are checked for sendability just before being sent, so there may be packets in
 */
class PacketTypeQueue {
	private final OrderingManager manager;

	/** The list of types that we cannot overtake. This is the list returned by the packet. If it is null it has not been populated */
	private Set<PacketTypeQueue> cannotOvertake = null;
	/** The list of types that cannot overtake us. */
	private final Set<PacketTypeQueue> cannotBeOvertaken = new HashSet<>();

	/**
	 * PRE-RECEIVE
	 */
	private final NavigableSet<SequencedPacket> preReceive = new TreeSet<>();

	/**
	 * RECEIVE
	 *
	 * oldest(RECEIVE) is younger than youngest(PROCESSING)
	 */
	private final PriorityQueue<SequencedPacket> received = new PriorityQueue<>(); //The oldest packet is at the head

	/**
	 * PROCESSING
	 */
	private final NavigableSet<SequencedPacket> processing = new TreeSet<>();

	/**
	 * The oldest packet that can be received.
	 */
	private SequencedPacket trim;

	/**
	 * youngest(DONE, PROCESSING) of all types that we cannot be overtaken by.
	 * The youngest packet that has been started that this queue cannot be overtaken by
	 */
	private SequencedPacket youngestPacketStarted = null;

	PacketTypeQueue(OrderingManager manager) {
		this.manager = manager;
	}

	/**
	 * Notifies the system that a packet exists, this method should only be used for reliable or high priority packets.
	 *
	 * If this packet cannot be executed ever, it will be immediately dropped
	 **/
	void preReceive(SequencedPacket packet) {
		//If this packet is older than the youngest packet that has started processing, then it can never be started
		if(youngestPacketStarted != null && packet.compareTo(youngestPacketStarted) < 0) {
			manager.dropPacket(packet);
		} else {
			preReceive.add(packet);
		}
	}

	/**
	 * Places a packet in RECEIVED
	 *
	 * If this packet cannot be executed ever, it will be immediately dropped
	 */
	void add(SequencedPacket packet) {
		if(cannotOvertake == null) {
			populateLinkages(packet.packet());
		}

		preReceive.remove(packet);

		// If a packet has started that should be after this one, we cannot run.
		if(youngestPacketStarted != null && packet.compareTo(youngestPacketStarted) < 0) {
			manager.dropPacket(packet);
		} else {
			received.add(packet);

			//If this packet is the oldest one, then it might be possible to send it.
			if(received.element().equals(packet)) {
				checkProcessing();
			}
		}
	}

	/** This populates the two dependency sets with information. */
	private void populateLinkages(Packet packet) {
		String schemaName = packet.schemaName();
		cannotOvertake = new HashSet<>();

		for(int id : packet.orderedIds()) {
			PacketType type = new PacketType(schemaName, id);

			var queue = manager.getQueue(type);
			queue.cannotBeOvertaken.add(this);
			cannotOvertake.add(queue);
		}
	}

	/**
	 * Checks if a packet can be processed, this should be called after RECEIVED grows, or, a packet that blocks this queue
	 * finished processing.
	 */
	private void checkProcessing() {
		if(received.isEmpty()) {
			return;
		}

		SequencedPacket oldestPacket = received.element();

		for(PacketTypeQueue queue : cannotBeOvertaken) {
			SequencedPacket otherPacket = queue.oldestPacketNotDone();

			//If the other packet should be sent before this one, we cannot send.
			if(otherPacket != null && otherPacket.compareTo(oldestPacket) < 0) {
				return;
			}
		}

		//Move the packet to PROCESSING
		manager.executePacket(oldestPacket);
		processing.add(oldestPacket);
		received.remove();

		if(manager.trim() == null || manager.trim() - oldestPacket.sequenceNumber() < 0) {
			//Notify the other queues of the packet we just sent.
			for(PacketTypeQueue queue : cannotBeOvertaken) {
				if(queue.youngestPacketStarted == null || queue.youngestPacketStarted.compareTo(oldestPacket) < 0) {
					queue.youngestPacketStarted = oldestPacket;
				}
			}
		}
	}

	/**
	 * Returns the oldest packet not in DONE. This returns null if there is no such packet.
	 */
	private SequencedPacket oldestPacketNotDone() {
		SequencedPacket oldest = null;

		if(!preReceive.isEmpty()) {
			oldest = preReceive.first();
		}

		if(!processing.isEmpty()) {
			var possibleOldest = processing.first();

			if(oldest == null || possibleOldest.compareTo(oldest) < 0) {
				oldest = possibleOldest;
			}
		} else if(!received.isEmpty()) {
			var possibleOldest = received.element();

			if(oldest == null || possibleOldest.compareTo(oldest) < 0) {
				oldest = possibleOldest;
			}
		}

		return oldest;
	}

	/**
	 * Notifies this queue that the listed packet has finished executing.
	 **/
	void notifyExecutionFinished(SequencedPacket packet) {
		//Move this packet to DONE, also check any of the packet types that may not overtake this one.

		processing.remove(packet);

		for(PacketTypeQueue queue : cannotOvertake) {
			queue.checkProcessing();
		}
	}

	/**
	 * This method should be called when it is guaranteed that there will be no packets older than the given packet received.
	 */
	void trim(int trim) {
		//If the youngestPacketStarted is older than, or equal to the trim, discard it.
		//We subtract here to cause overflow if needed, it is intended, as without it it would not follow the correct contract for sequence numbers.
		if(youngestPacketStarted != null && youngestPacketStarted.sequenceNumber() - trim <= 0) {
			youngestPacketStarted = null;
		}
	}

	/** Returns the oldest packet referenced by this queue. This may be null if there are no items in this queue. */
	SequencedPacket oldestPacket() {
		var oldest = oldestPacketNotDone();

		if(oldest == null || (youngestPacketStarted != null && youngestPacketStarted.compareTo(oldest) < 0)) {
			oldest = youngestPacketStarted;
		}

		return oldest;
	}
}
