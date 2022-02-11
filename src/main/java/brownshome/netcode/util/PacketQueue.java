package brownshome.netcode.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.UnaryOperator;

/**
 * Manages an ordered queue of actions on packets.
 *
 * For the internal bitsets used for communication, bit zero is a barrier and should be waited on by everyone
 */
public abstract class PacketQueue {
	private static final int BARRIER_ID = 0;

	protected interface ScheduledItem {
		/**
		 * Processes this type
		 * @param previous a bitset representing the previous types in the queue
		 * @return true if the type should be removed from the queue
		 */
		boolean processType(BitSet previous);

		static boolean hasBarrier(BitSet previous) {
			return previous.get(BARRIER_ID);
		}

		static void setBarrier(BitSet previous) {
			previous.set(BARRIER_ID);
		}
	}

	protected record Flush(CompletableFuture<Void> flushFuture) implements ScheduledItem {
		@Override
		public boolean processType(BitSet previous) {
			if (previous.isEmpty()) {
				flushFuture.complete(null);
			}

			return flushFuture.isDone();
		}
	}

	protected record Wait(CompletableFuture<Void> waitFuture) implements ScheduledItem {
		@Override
		public boolean processType(BitSet previous) {
			if (!waitFuture.isDone()) {
				ScheduledItem.setBarrier(previous);
			}

			return waitFuture.isDone();
		}
	}

	protected record Barrier(CompletableFuture<Void> start, CompletableFuture<Void> end) implements ScheduledItem {
		@Override
		public boolean processType(BitSet previous) {
			if (previous.isEmpty()) {
				start.complete(null);
			}

			if (!end.isDone()) {
				ScheduledItem.setBarrier(previous);
			}

			return end.isDone();
		}
	}

	private final Queue<ScheduledItem> scheduled;

	protected PacketQueue() {
		scheduled = new ConcurrentLinkedQueue<>();
	}

	protected final void schedule(ScheduledItem item) {
		scheduled.add(item);
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

	/**
	 * Checks the schedule for packets that can start. This must be called by implementers of this class upon changes that might
	 * require processing.
	 */
	protected final void sweepSchedule() {
		BitSet older = new BitSet();

		// Don't collapse into removeIf; the iteration order and side-effects are important here. RemoveIf does not gurantee
		// these will be respected.
		for (var it = scheduled.iterator(); it.hasNext(); ) {
			if (it.next().processType(older)) {
				it.remove();
			}
		}
	}
}
