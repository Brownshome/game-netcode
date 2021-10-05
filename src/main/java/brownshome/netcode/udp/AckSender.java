package brownshome.netcode.udp;

import java.util.BitSet;

/** This class records what packets have been received and produces the requested Ack fields for sending. */
final class AckSender {
	private final BitSet receivedAcks = new BitSet();
	private final BitSet unsentAcks = new BitSet();

	void receivedPacket(int sequenceNumber) {
		receivedAcks.set(sequenceNumber);
		unsentAcks.set(sequenceNumber);
	}

	boolean hasUnsentAcks() {
		return !unsentAcks.isEmpty();
	}

	static final class OutgoingAck {
		final int largestAck;
		final int field;

		private OutgoingAck(int largestAck, int field) {
			this.largestAck = largestAck;
			this.field = field;
		}
	}

	/** Creates an ack field */
	OutgoingAck createAck() {
		// TODO use rolling acks, and not BitSet

		if (receivedAcks.isEmpty()) {
			return new OutgoingAck(0, 0);
		}

		int oldestAck, newestAck;

		decideBounds: {
			oldestAck = unsentAcks.nextSetBit(0);

			if (oldestAck != -1) {
				newestAck = oldestAck + Integer.SIZE - 1;
				break decideBounds;
			}

			newestAck = receivedAcks.length() - 1;
			oldestAck = newestAck - 1;
		}

		int field = 0;

		// bit n = newestAck - n where n goes from 0 to SIZE - 1
		for (int bit = Integer.SIZE - 1; bit >= 0; bit--) {
			field <<= 1;

			if (newestAck - bit >= 0) {
				field |= receivedAcks.get(newestAck - bit) ? 1 : 0;
			}
		}

		unsentAcks.clear(Math.max(oldestAck, 0), newestAck + 1);

		return new OutgoingAck(newestAck, field);
	}
}
