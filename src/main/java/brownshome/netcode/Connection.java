package brownshome.netcode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface Connection<ADDRESS> extends AutoCloseable {
	/**
	 * Sends a packet. If this connection has not yet connected, then the packet will be sent as soon as the connection
	 * is made. If this future errors then the connection is in an error state, and will close. This may occur with
	 * bandwidth overloads and should be considered non-recoverable.
	 *
	 * @return A future that will return when the packet has been sent. In the case of a reliable packet it will return when it
	 * has been received.
	 */
	CompletableFuture<Void> send(Packet packet);

	default void sendSync(Packet packet) throws InterruptedException, NetworkException {
		awaitFuture(send(packet));
	}

	/**
	 * Waits until all packets have been sent, or reliably received.
	 *
	 * If a send call on another thread overlaps with the send and flush calls on this thread, it is undefined whether
	 * that send will be flushed or not by this call, even if the packet on the other thread was sent first. It is up
	 * to the user to ensure that send calls do not overlap with the flush call in this way if flushes should respect
	 * packet send order.
	 *
	 * @return A future that will return when all of the packets have been sent, or in the case of reliable packets,
	 * received.
	 **/
	CompletableFuture<Void> flush();

	default void flushSync() throws InterruptedException, NetworkException {
		awaitFuture(flush());
	}

	/** Gets that address object for this connection. */
	ADDRESS address();

	/** Attempts to connect to the host at the other end. This method has no effect if the connection has already been
	 * connected.
	 * @return A future that represents when a connection has been made successfully.
	 **/
	CompletableFuture<Void> connect();

	default void connectSync() throws InterruptedException, NetworkException {
		awaitFuture(connect());
	}

	/**
	 * Closes the connection. This will send all packets that have been already queued up, and then terminate the connection.
	 * Closing a connection that has already been closed will have no effect.
	 * @return A future that returns when the connection has been closed cleanly.
	 */
	CompletableFuture<Void> closeConnection();

	/**
	 * This is for the Closeable interface, it initiates the closing procedure and the immediately returns.
	 *
	 * If more control is needed, use the awaitCloseConnection or closeConnection methods.
	 */
	@Override
	default void close() {
		try {
			awaitCloseConnection();
		} catch(InterruptedException e) {
			throw new NetworkException(e, this);
		}
	}

	default void awaitCloseConnection() throws InterruptedException, NetworkException {
		awaitFuture(closeConnection());
	}

	default <T> T awaitFuture(Future<T> future) throws InterruptedException, NetworkException {
		try {
			return future.get();
		} catch(ExecutionException ee) {
			Throwable cause = ee.getCause();

			if(cause instanceof NetworkException) {
				throw (NetworkException) cause;
			} else {
				throw new NetworkException(cause, this);
			}
		}
	}

	/** Returns the connection manager that created this connection */
	ConnectionManager<ADDRESS, ? extends Connection<ADDRESS>> connectionManager();

	/** Returns the protocol that is used by this connection. */
	Protocol protocol();
}
