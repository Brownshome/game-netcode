package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

/**
 * This class represents a packet with an attached sequence number.
 *
 * NOTE: This does not correctly implement comparable, integer wrap-around means
 * that it is possible for a < b and b < c but c < a. This is by design, and should be noted when using this
 * class.
 **/
//TODO possible split this class?
public final class SequencedPacket implements Comparable<SequencedPacket> {
	private final PacketType packetType;
	private final Packet packet;
	private final int sequenceNumber;

	public SequencedPacket(Packet packet, int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
		this.packetType = new PacketType(packet);
		this.packet = packet;
	}

	public PacketType packetType() {
		return packetType;
	}

	public Packet packet() {
		return packet;
	}

	public int sequenceNumber() {
		return sequenceNumber;
	}

	public SequencedPacket(PacketType type, int sequenceNumber) {
		this.packetType = type;
		this.packet = null;
		this.sequenceNumber = sequenceNumber;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(!(o instanceof SequencedPacket)) return false;
		SequencedPacket that = (SequencedPacket) o;
		return sequenceNumber == that.sequenceNumber;
	}

	@Override
	public int hashCode() {
		return sequenceNumber;
	}

	@Override
	public int compareTo(SequencedPacket o) {
		return sequenceNumber - o.sequenceNumber;
	}
}
