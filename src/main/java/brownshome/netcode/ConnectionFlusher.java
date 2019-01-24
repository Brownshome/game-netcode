package brownshome.netcode;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * This class keeps track of what packets have been sent and keeps track of what packets have not yet been sent.
 *
 * Packets that error, or are canceled are considered flushed, the future will never error, or cancel itself.
 *
 * This class may have any of its methods called from any thread.
 **/
public final class ConnectionFlusher {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");

	private final class QueueItem {
		final boolean isFlushQuery;
		final CompletableFuture<Void> future;

		QueueItem(CompletableFuture<Void> future) {
			this.isFlushQuery = false;
			this.future = future;
		}

		QueueItem() {
			this.isFlushQuery = true;
			this.future = new CompletableFuture<>();
		}
	}

	private final Queue<QueueItem> queue;

	public ConnectionFlusher() {
		queue = new ArrayDeque<>();
	}

	/** Gets a future that returns when all packets sent before this call have been flushed */
	public synchronized CompletableFuture<Void> flush() {
		// If there is nothing in the queue we can't add a flush marker, as there is nothing to call it.

		if(queue.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		} else {
			QueueItem item = new QueueItem();
			queue.add(item);
			return item.future;
		}
	}

	/** Submits a future to wait on to the flusher. Any calls to flush after this point will wait for this future to complete. */
	public synchronized void submitFuture(CompletableFuture<Void> future) {
		queue.add(new QueueItem(future));

		future.handle((unused, exception) -> {
			futureCompleted();
			return null;
		});
	}

	/** This method is called when any of the futures waiting in the queue completes. */
	private synchronized void futureCompleted() {
		// No matter the outcome of the future. Any state is considered 'flushed'

		// It is guaranteed that future is complete. When this method is called.

		while(!queue.isEmpty()) {
			QueueItem next = queue.element();

			if(!next.isFlushQuery && !next.future.isDone()) {
				break;
			}

			if(next.isFlushQuery) {
				next.future.complete(null);
			}

			queue.remove();
		}
	}
}
