package brownshome.netcode.util;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class PacketQueue<T> {
	private final List<ScheduledItem<T>> scheduled;

	public PacketQueue() {
		scheduled = new LinkedList<>();
	}

	protected void schedule(ScheduledItem<T> item) {
		scheduled.add(item);
	}

	protected List<ScheduledItem<T>> scheduled() {
		return scheduled;
	}

	/**
	 * Checks the schedule for packets that can start. This must be called by implementers of this class upon changes that might
	 * require processing.
	 */
	protected void sweepSchedule() {
		var older = startingCommunication();

		// Don't collapse into removeIf; the iteration order and side-effects are important here. RemoveIf does not gurantee
		// these will be respected.
		for (var it = scheduled().iterator(); it.hasNext(); ) {
			var result = it.next().processType(older);

			if (result.shouldRemove()) {
				it.remove();
			}

			older = result.communication();
		}
	}

	protected abstract T startingCommunication();

	protected record ProcessingResult<T>(T communication, boolean shouldRemove) {
		public static <T> ProcessingResult<T> remove(T communication) {
			return new ProcessingResult<>(communication, true);
		}

		public static <T> ProcessingResult<T> keep(T communication) {
			return new ProcessingResult<>(communication, false);
		}
	}

	protected interface ScheduledItem<T> {
		/**
		 * Processes this type
		 *
		 * @param previous an object representing the previous types in the queue
		 * @return the result of processing
		 */
		ProcessingResult<T> processType(T previous);
	}
}
