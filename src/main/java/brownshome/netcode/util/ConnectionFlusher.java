package brownshome.netcode.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class keeps track of what packets have been sent and keeps track of what packets have not yet been sent.
 *
 * Packets that error, or are canceled are considered flushed, the future will never error, or cancel itself.
 *
 * This class may have any of its methods called from any thread.
 **/
public final class ConnectionFlusher {
	private final AtomicReference<CompletableFuture<Void>> latestFuture = new AtomicReference<>(CompletableFuture.completedFuture(null));

	/**
	 * Gets a future that returns when all packets sent before this call have been flushed
	 **/
	public CompletableFuture<Void> flush() {
		return latestFuture.get();
	}

	/** Submits a future to wait on to the flusher. Any calls to flush after this point will wait for this future to complete. */
	public void submitFuture(CompletableFuture<Void> future) {
		latestFuture.updateAndGet(flush -> CompletableFuture.allOf(flush, future));
	}
}
