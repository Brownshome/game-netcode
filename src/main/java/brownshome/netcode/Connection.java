package brownshome.netcode;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

import brownshome.netcode.util.PacketExecutor;

public abstract class Connection<ADDRESS, CONNECTION_MANAGER extends ConnectionManager<ADDRESS, ?>> implements AutoCloseable {
	private final CONNECTION_MANAGER connectionManager;
	private final ADDRESS address;

	private final PacketExecutor packetExecutor;

	private Protocol protocol;

	protected Connection(CONNECTION_MANAGER connectionManager,
	                     ADDRESS address,
	                     Protocol initialProtocol) {
		this.connectionManager = connectionManager;
		this.address = address;

		// Note: This field is non-final and so needs synchronisation if it is read post-construction in another thread
		this.protocol = initialProtocol;
		this.packetExecutor = new PacketExecutor(this);
	}

	/**
	 * Executes an incoming packet on the correct handler. This method also ensures that the packet is not executed before
	 * any packets that should have a happens-before relationship with it for this connection.
	 * @param packet the packet to execute
	 * @return a future representing the result of executing the packet
	 */
	protected CompletableFuture<Void> execute(Packet packet) {
		return packetExecutor.execute(packet);
	}

	/**
	 * Flushes all executing packets
	 * @return a future that completes when all packets have flushed
	 */
	protected final CompletableFuture<Void> executionFlush() {
		return packetExecutor.flush();
	}

	/**
	 * Causes all executed packets and flushes to wait for a given future
	 * @param wait the future to wait for
	 */
	protected final void executionWait(CompletableFuture<Void> wait) {
		packetExecutor.wait(wait);
	}

	/**
	 * Inserts an execution barrier
	 * @param barrier a function that takes a future representing the start of the barrier and returns the end of the barrier
	 * @return the future representing the end of the barrier
	 */
	protected final CompletableFuture<Void> executionBarrier(UnaryOperator<CompletableFuture<Void>> barrier) {
		return packetExecutor.barrier(barrier);
	}

	/**
	 * Sends a packet. If this connection has not yet connected, then the packet will be sent as soon as the connection
	 * is made. If this future errors then the connection is in an error state, and will close. This may occur with
	 * bandwidth overloads and should be considered non-recoverable.
	 *
	 * @return A future that will return when the packet has been sent. In the case of a reliable packet it will return when it
	 * has been received.
	 */
	public abstract CompletableFuture<Void> send(Packet packet);

	public final void sendSync(Packet packet) throws InterruptedException, NetworkException {
		awaitFuture(send(packet));
	}

	/**
	 * Waits until all packets have been sent, or reliably received.
	 *
	 * If a send-call on another thread overlaps with the send-calls and flush-calls on this thread, it is undefined whether
	 * that send will be flushed or not by this call, even if the packet on the other thread was sent first. It is up
	 * to the user to ensure that send calls do not overlap with the flush call in this way if flushes should respect
	 * packet send order.
	 *
	 * @return A future that will return when all of the packets have been sent, or in the case of reliable packets,
	 * received.
	 **/
	public abstract CompletableFuture<Void> flush();

	public final void flushSync() throws InterruptedException, NetworkException {
		awaitFuture(flush());
	}

	/** Gets that address object for this connection. */
	public final ADDRESS address() {
		return address;
	}

	/**
	 * Attempts to negotiate a connection to the host at the other end.
	 * @return A future that represents when a connection has been made successfully.
	 **/
	public final  CompletableFuture<Void> connect() {
		return connect(connectionManager().schemas());
	}

	public final  void connectSync() throws InterruptedException, NetworkException {
		awaitFuture(connect());
	}

	public abstract CompletableFuture<Void> connect(List<Schema> schemas);

	public final void connectSync(List<Schema> schemas) throws InterruptedException, NetworkException {
		awaitFuture(connect(schemas));
	}

	/**
	 * Closes the connection. This will send all packets that have been already queued up, and then terminate the connection.
	 * Closing a connection that has already been closed will have no effect.
	 * @return A future that returns when the connection has been closed cleanly.
	 */
	public abstract CompletableFuture<Void> closeConnection();

	/**
	 * This is for the Closeable interface, it initiates the closing procedure and the immediately returns.
	 *
	 * If more control is needed, use the awaitCloseConnection or closeConnection methods.
	 */
	@Override
	public final void close() throws InterruptedException, NetworkException {
		closeSync();
	}

	public final void closeSync() throws InterruptedException, NetworkException {
		awaitFuture(closeConnection());
	}

	private <T> T awaitFuture(Future<T> future) throws InterruptedException, NetworkException {
		try {
			return future.get();
		} catch (ExecutionException ee) {
			Throwable cause = ee.getCause();

			if (cause instanceof NetworkException) {
				throw (NetworkException) cause;
			} else {
				throw new NetworkException(cause, this);
			}
		}
	}

	/**
	 * The connection manager that created this connection
	 * @return the connection manager
	 **/
	public final CONNECTION_MANAGER connectionManager() {
		return connectionManager;
	}

	/**
	 * The protocol that is used by this connection.
	 * @return the protocol
	 **/
	public final Protocol protocol() {
		return protocol;
	}

	protected final void protocol(Protocol protocol) {
		this.protocol = protocol;
	}
}
