package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

/**
 * This class represents a packet with an attached sequence number.
 *
 * NOTE: This does not correctly implement comparable, integer wrap-around means
 * that it is possible for a < b and b < c but c < a. This is by design, and should be noted when using this
 * class.
 **/
public record SequencedPacket(PacketType packetType, Packet packet, int sequenceNumber) implements Comparable<SequencedPacket> {
	public SequencedPacket(Packet packet, int sequenceNumber) {
		this(new PacketType(packet), packet, sequenceNumber);
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
