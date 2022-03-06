package brownshome.netcode.udp;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A pool of sequence number awaiting acknowledgement. This class allocates new sequence numbers and signals futures
 * when they are acknowledged.
 */
final class SequenceNumberPool {
	private int nextSequenceNumber = 0;
	@SuppressWarnings("unchecked")
	private CompletableFuture<Void>[] futures = (CompletableFuture<Void>[]) new CompletableFuture<?>[64];

	/**
	 * Allocates a new sequence number and stores the future for activation later. The future may be null.
	 * @param receiveFuture a future to complete when the packet is acknowledged
	 * @return a new sequence number. Sequence numbers will increase monotonically, but will wrap around eventually
	 */
	synchronized int allocateSequenceNumber(CompletableFuture<Void> receiveFuture) {
		int freeIndex = index(nextSequenceNumber);
		var oldFuture = futures[freeIndex];

		if (oldFuture != null && !oldFuture.isDone()) {
			// Expand the array
			int oldFreeIndex = freeIndex;
			futures = Arrays.copyOf(futures, futures.length);
			freeIndex = index(nextSequenceNumber);

			if (oldFreeIndex == freeIndex) {
				// Move the tail to the end of the array
				System.arraycopy(futures, oldFreeIndex, futures, freeIndex, futures.length - freeIndex);
				Arrays.fill(futures, oldFreeIndex, futures.length / 2, null);
			} else {
				// Move the head up the array
				System.arraycopy(futures, 0, futures, futures.length / 2, oldFreeIndex);
				Arrays.fill(futures, 0, freeIndex, null);
			}
		}

		futures[freeIndex] = receiveFuture;
		return nextSequenceNumber++;
	}

	/**
	 * Informs the pool that a packed has been acknowledged
	 * @param sequenceNumber the sequence number to acknowledge
	 */
	synchronized void onAcknowledgementReceived(int sequenceNumber) {
		if (sequenceNumber < oldestSequenceNumber()) {
			return;
		}

		int index = index(sequenceNumber);
		var future = futures[index];
		if (future != null) {
			future.complete(null);
		}
	}

	private int index(int sequenceNumber) {
		return (int) (Integer.toUnsignedLong(sequenceNumber) % futures.length);
	}

	private int oldestSequenceNumber() {
		return nextSequenceNumber - futures.length;
	}
}
