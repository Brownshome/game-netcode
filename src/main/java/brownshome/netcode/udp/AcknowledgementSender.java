package brownshome.netcode.udp;

import java.util.Arrays;

final class AcknowledgementSender {
	private static final System.Logger LOGGER = System.getLogger(AcknowledgementSender.class.getModule().getName());

	private int nextFreeIndex = 0;

	// This is a large minimum size to allow acknowledgements to be queued up
	private long[] receivedPackets = new long[64];
	private long[] pendingAcknowledgements = new long[64];

	{
		Arrays.fill(receivedPackets, -1L);
	}

	synchronized boolean onSequenceNumberReceived(int sequenceNumber, boolean queueAcknowledgement) {
		int index = Integer.divideUnsigned(sequenceNumber, Long.SIZE);
		long bit = 1L << Integer.remainderUnsigned(sequenceNumber, Long.SIZE);

		index = ensureIndexExists(index);

		if (index == -1 || (receivedPackets[index] & bit) != 0L) {
			return false;
		}

		if (queueAcknowledgement) {
			pendingAcknowledgements[index] |= bit;
		}

		receivedPackets[index] |= bit;

		return true;
	}

	/**
	 * Expands the array if needed to contain the requested index
	 * @param index the index to translate
	 * @return the slot in the array that the index can be found at
	 */
	private int ensureIndexExists(int index) {
		if (index - (nextFreeIndex - receivedPackets.length) < 0) {
			// We are off the end of the array
			return -1;
		}

		int i = Integer.remainderUnsigned(nextFreeIndex, receivedPackets.length);
		while (index - nextFreeIndex >= 0) {
			if (receivedPackets[i] != -1L || pendingAcknowledgements[i] != 0L) {
				// Expand the arrays
				int oldLength = receivedPackets.length;
				receivedPackets = Arrays.copyOf(receivedPackets, oldLength * 2);
				pendingAcknowledgements = Arrays.copyOf(pendingAcknowledgements, oldLength * 2);

				int oldArrayIndex = i;
				i = Integer.remainderUnsigned(nextFreeIndex, receivedPackets.length);

				if (i == oldArrayIndex) {
					// Move the tail to the end of the array
					// [ HHH|TTT ] -> [ HHH|xxx xxx TTT ]
					System.arraycopy(receivedPackets, i, receivedPackets, i + oldLength, oldLength - i);
					Arrays.fill(receivedPackets, i, i + oldLength, -1L);

					System.arraycopy(pendingAcknowledgements, i, pendingAcknowledgements, i + oldLength, oldLength - i);
					Arrays.fill(pendingAcknowledgements, i, oldLength, 0L);
				} else {
					// Move the head up the array
					// [ HHH|TTT ] -> [ xxx TTT HHH|xxx ]
					System.arraycopy(receivedPackets, 0, receivedPackets, oldLength, oldArrayIndex);
					Arrays.fill(receivedPackets, 0, oldArrayIndex, -1L);
					Arrays.fill(receivedPackets, i, receivedPackets.length, -1L);

					System.arraycopy(pendingAcknowledgements, 0, pendingAcknowledgements, oldLength, oldArrayIndex);
					Arrays.fill(pendingAcknowledgements, 0, i, 0L);
				}

				LOGGER.log(System.Logger.Level.TRACE, "Expanding acknowledgement buffer from {0} to {1}", oldLength, receivedPackets.length);
			}

			receivedPackets[i] = 0L;

			i = Integer.remainderUnsigned(++nextFreeIndex, receivedPackets.length);
		}

		return i;
	}

	synchronized Acknowledgement constructAcknowledgementField() {
		if (nextFreeIndex == 0) {
			return Acknowledgement.emptyAcknowledgement();
		}

		for (int i = nextFreeIndex - receivedPackets.length - 1; i != nextFreeIndex; i++) {
			int slot = Integer.remainderUnsigned(i, receivedPackets.length);

			if (pendingAcknowledgements[slot] != 0) {
				return constructAcknowledgementField(i * Long.SIZE + Long.numberOfTrailingZeros(pendingAcknowledgements[slot]));
			}
		}

		return constructAcknowledgementField(nextFreeIndex * Long.SIZE);
	}

	synchronized Acknowledgement constructAcknowledgementField(int oldestSequenceNumber) {
		if (nextFreeIndex == 0) {
			return Acknowledgement.emptyAcknowledgement();
		}

		// Don't allow the field to overflow the head of the array
		oldestSequenceNumber = Math.min(nextFreeIndex * Long.SIZE - 1, oldestSequenceNumber);

		// We can use bits and slots greater than these
		int slot = Integer.divideUnsigned(oldestSequenceNumber, Long.SIZE) % receivedPackets.length;
		int bit = Integer.remainderUnsigned(oldestSequenceNumber, Long.SIZE);

		int bitField = (int) (receivedPackets[slot] >>> bit);
		pendingAcknowledgements[slot] &= ~(0xffffffffL << bit);

		if (bit > Long.SIZE - Integer.SIZE) {
			// Add more bits
			int secondSlot = (slot + 1) % receivedPackets.length;
			bitField |= receivedPackets[secondSlot] << (Long.SIZE - bit);
			pendingAcknowledgements[secondSlot] &= ~(0xffffffffL >>> (Long.SIZE - bit));
		}

		return new Acknowledgement(oldestSequenceNumber, bitField);
	}
}
