package brownshome.netcode;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface Connection<ADDRESS> {	
	/**
	 * Sends a packet.
	 * @return A future that will return when the packet has been sent. In the case of a reliable packet it will return when it
	 * has been received.
	 */
	Future<Void> send(Packet packet);

	default void sendSync(Packet packet) throws InterruptedException, NetworkException {
		awaitFuture(send(packet));
	}

	/** Waits until all packets have been sent.
	 * @return A future that will return when all of the packets have been sent.
	 **/
	Future<Void> flush();

	default void flushSync(Packet packet) throws InterruptedException, NetworkException {
		awaitFuture(flush());
	}

	/** Gets that address object for this connection. */
	ADDRESS getAddress();

	/** Attempts to connect to the host at the other end.
	 * @return A future that represents when a connection has been made successfully.
	 **/
	Future<Void> connect();
	
	default void connectSync() throws InterruptedException, NetworkException {
		awaitFuture(connect());
	}

	/**
	 * Closes the connection
	 * @return A future that returns when the connection has been closed cleanly.
	 */
	Future<Void> close();
	
	default void awaitClose() throws InterruptedException, NetworkException {
		awaitFuture(close());
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
	
	ConnectionManager<ADDRESS, ? extends Connection<ADDRESS>> getConnectionManager();
	
	NetworkProtocol getProtocol();

	void setProtocol(NetworkProtocol networkProtocol);
}
