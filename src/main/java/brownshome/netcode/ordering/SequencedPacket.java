package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

/**
 * This class represents a packet with an attached sequence number.
 *
 * NOTE: This does not correctly implement comparable, integer wrap-around means
 * that it is possible for a < b and b < c but c < a. This is by design, and should be noted when using this
 * class.
 **/
final class SequencedPacket implements Comparable<SequencedPacket> {
	private final PacketType packetType;
	private Packet packet;
	private final int sequenceNumber;

	SequencedPacket(Packet packet, int sequenceNumber) {
		this(new PacketType(packet), sequenceNumber);

		this.packet = packet;
	}

	PacketType packetType() {
		return packetType;
	}

	Packet packet() {
		return packet;
	}

	int sequenceNumber() {
		return sequenceNumber;
	}

	SequencedPacket(PacketType type, int sequenceNumber) {
		this.packetType = type;
		this.sequenceNumber = sequenceNumber;
	}

	void setPacketReceived(Packet packet) {
		assert new PacketType(packet).equals(packetType);

		this.packet = packet;
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
