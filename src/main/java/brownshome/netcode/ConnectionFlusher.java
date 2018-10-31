package brownshome.netcode;

import java.util.concurrent.*;
import java.util.logging.Logger;

/** This class keeps track of what packets have been sent and keeps track of what packets have not yet been sent.
 * This thread is not a daemon thread by default.
 *
 * Packets that error, or are canceled are considered flushed, the future will never error, or cancel itself.
 **/
public final class ConnectionFlusher extends Thread {
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

	private final BlockingQueue<QueueItem> queue;
	private final Connection<?> connection;

	public ConnectionFlusher(Connection<?> connection) {
		super(String.format("NETCODE-FLUSHER-%s", connection.address()));

		this.connection = connection;
		queue = new LinkedBlockingQueue<>();
	}

	@Override
	public void run() {
		while(true) {
			try {
				QueueItem item = queue.take();

				if(item.isFlushQuery) {
					item.future.complete(null);
				} else {
					item.future.get();
				}
			} catch(InterruptedException e) {
				//Exit the loop, and die.
				ConnectionFlusher.LOGGER.info(String.format("%s flusher dying due to interrupt.", connection.address()));
				return;
			} catch(ExecutionException | CancellationException e) {
				//Do nothing, the packet is considered 'flushed'
			}
		}
	}

	/** Gets a future that returns when all packets sent before this call have been flushed */
	public CompletableFuture<Void> flush() {
		QueueItem item = new QueueItem();

		queue.add(item);

		return item.future;
	}

	public void submitFuture(CompletableFuture<Void> future) {
		queue.add(new QueueItem(future));
	}
}
