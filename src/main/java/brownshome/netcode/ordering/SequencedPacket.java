package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

/**
 * This class represents a packet with an attached sequence number.
 *
 * NOTE: This does not correctly implement comparable, integer wrap-around means
 * that it is possible for {@code a < b} and {@code b < c} but {@code c < a}. This is by design, and should be noted when using this
 * class.
 **/
public record SequencedPacket(Packet packet, int sequenceNumber) implements Comparable<SequencedPacket> {
	@Override
	public int hashCode() {
		return sequenceNumber;
	}

	@Override
	public int compareTo(SequencedPacket o) {
		return sequenceNumber - o.sequenceNumber;
	}
}
