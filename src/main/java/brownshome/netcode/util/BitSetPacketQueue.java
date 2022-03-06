package brownshome.netcode.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Manages an ordered queue of actions on packets.
 *
 * For the internal bitsets used for communication, bit zero is a barrier and should be waited on by everyone
 */
public class BitSetPacketQueue extends PacketQueue<BitSet> {
	private static final int BARRIER_ID = 0;

	protected static void setBarrier(BitSet bitSet) {
		bitSet.set(BARRIER_ID);
	}

	protected static boolean hasBarrier(BitSet bitSet) {
		return bitSet.get(BARRIER_ID);
	}

	@Override
	protected BitSet startingCommunication() {
		return new BitSet();
	}

	protected record Flush(CompletableFuture<Void> flushFuture) implements ScheduledItem<BitSet> {
		@Override
		public ProcessingResult<BitSet> processType(BitSet previous) {
			if (previous.isEmpty()) {
				flushFuture.complete(null);
			}

			return new ProcessingResult<>(previous, flushFuture.isDone());
		}
	}

	protected record Wait(CompletableFuture<Void> waitFuture) implements ScheduledItem<BitSet> {
		@Override
		public ProcessingResult<BitSet> processType(BitSet previous) {
			if (!waitFuture.isDone()) {
				setBarrier(previous);
			}

			return new ProcessingResult<>(previous, waitFuture.isDone());
		}
	}

	protected record Barrier(CompletableFuture<Void> start, CompletableFuture<Void> end) implements ScheduledItem<BitSet> {
		@Override
		public ProcessingResult<BitSet> processType(BitSet previous) {
			if (previous.isEmpty()) {
				start.complete(null);
			}

			if (!end.isDone()) {
				setBarrier(previous);
			}

			return new ProcessingResult<>(previous, end.isDone());
		}
	}

	/**
	 * Makes a future that completes when all previous submissions have been completed, including barriers and waits
	 * @return a future
	 */
	public CompletableFuture<Void> flush() {
		var future = new CompletableFuture<Void>();
		schedule(new Flush(future));

		// It might be already complete
		sweepSchedule();

		return future;
	}

	/**
	 * Causes all future executions, flushes and barriers to wait on this future to complete
	 * @param wait the future to wait for
	 */
	public void wait(CompletableFuture<Void> wait) {
		schedule(new Wait(wait));

		// Update when the future is called
		wait.whenComplete((unused, throwable) -> sweepSchedule());
	}

	/**
	 * Causes all future executions to wait for all previous executions and a given action to run
	 * @param barrier a transformation between the start of the barrier and the end of the barrier
	 */
	public CompletableFuture<Void> barrier(UnaryOperator<CompletableFuture<Void>> barrier) {
		var startFuture = new CompletableFuture<Void>();
		var finishFuture = barrier.apply(startFuture);

		schedule(new Barrier(startFuture, finishFuture));

		// Update when the barrier finishes, and it might already be complete
		finishFuture.whenComplete((unused, throwable) -> sweepSchedule());
		sweepSchedule();

		return finishFuture;
	}
}
