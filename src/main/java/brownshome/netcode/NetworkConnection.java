package brownshome.netcode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import brownshome.netcode.util.ConnectionFlusher;

public abstract class NetworkConnection<ADDRESS> implements Connection<ADDRESS> {
	public enum State {
		/** The connection is not connected. Packets will be sent when the connection is ready. */
		NO_CONNECTION,

		/** The connection is negotiating a protocol. Packets will be sent when this protocol is negotiated. */
		NEGOTIATING,

		/** The connection is ready to send packets. */
		READY,

		/** The connection is closed. Any attempt to send packets will fail. */
		CLOSED
	}

	/** This is the protocol that the connection is currently using, it starts off as the basic protocol, and then
	 * is refined by connection negotiations. */
	private Protocol protocol;

	/** The remote address that this connection is connected to. */
	private final ADDRESS address;

	/** Packets will not be sent is any mode other than READY */
	private State state;

	/** This caches the results of the close and connect packets so that they can be returned in later calls. */
	private CompletableFuture<Void> closeFuture, connectFuture;

	/** This future represents the presence of a confirmProtocol packet. */
	private CompletableFuture<Void> confirmReceivedFuture;

	/** This lock is ensure that no packet is sent after a connection negotiation packet until the confirmation returns */
	private final ReentrantReadWriteLock stateLock;

	private static final class QueuedPacket {
		QueuedPacket(Packet packet, CompletableFuture<Void> future) {
			this.packet = packet;
			this.future = future;
		}

		final Packet packet;
		final CompletableFuture<Void> future;
	}

	/**
	 * This is a buffer of packets that could not be sent due to connection state problems.
	 * This buffer will never have items in it in the READY or CLOSED state once the read lock is available.
	 **/
	private final List<QueuedPacket> sendBuffer;

	private final ConnectionFlusher flusher;

	/**
	 * Creates a connection.
	 */
	protected NetworkConnection(ADDRESS address) {
		this.address = address;
		this.state = State.NO_CONNECTION;
		this.stateLock = new ReentrantReadWriteLock();
		this.protocol = baseProtocol();
		this.sendBuffer = new ArrayList<>();
		this.flusher = new ConnectionFlusher();
	}

	/**
	 * Gets the state of the connection
	 */
	protected final State state() {
		return state;
	}

	/**
	 * This method returns the default set of protocols that are used to communicate before the connection is negotiated
	 */
	protected Protocol baseProtocol() {
		return Protocol.baseProtocol();
	}

	@Override
	public CompletableFuture<Void> send(Packet packet) {
		// The read lock must contain the sending line, as otherwise there might be a packet in the process of sending
		// when the state is changed from READY to NEGOTIATING
		try { stateLock.readLock().lock();
			return switch (state) {
				case CLOSED -> CompletableFuture.failedFuture(new NetworkException("The connection is closed.", this));

				case READY -> {
					var future = sendWithoutStateChecks(packet);
					flusher.submitFuture(future);
					yield future;
				}

				case NO_CONNECTION, NEGOTIATING -> {
					var future = new CompletableFuture<Void>();

					sendBuffer.add(new QueuedPacket(packet, future));
					flusher.submitFuture(future);

					yield future;
				}
			};
		} finally { stateLock.readLock().unlock(); }
	}

	/**
	 * This is the method that is used to send packets internally. Override this.
	 * @see #send(Packet)
	 */
	protected abstract CompletableFuture<Void> sendWithoutStateChecks(Packet packet);

	@Override
	public CompletableFuture<Void> flush() {
		return flusher.flush();
	}

	@Override
	public ADDRESS address() {
		return address;
	}

	@Override
	public CompletableFuture<Void> connect(List<Schema> schemas) {
		try { stateLock.writeLock().lock();
			return switch (state) {
				case CLOSED -> connectFuture = CompletableFuture.failedFuture(new NetworkException("The connection is closed.", this));

				case NO_CONNECTION -> {
					state = State.NEGOTIATING;

					// Start waiting for the confirmProtocolPacket
					confirmReceivedFuture = new CompletableFuture<>();

					CompletableFuture<Void> negotiateSentPacket = sendWithoutStateChecks(new NegotiateProtocolPacket(schemas));
					flusher.submitFuture(negotiateSentPacket);

					// This is needed to detect sending failures. If we just wait for the received then if the negotiate-packet
					// fails this future locks up. The future still may lock up if the confirmation fails, but this is a little
					// bit more friendly.
					yield connectFuture = CompletableFuture.allOf(negotiateSentPacket, confirmReceivedFuture);
				}

				case READY -> {
					state = State.NEGOTIATING;

					// When we have flushed all packets we can safely re-negotiate the connection
					yield connectFuture = flush().thenCompose(v -> {
						try { stateLock.writeLock().lock();
							state = State.NO_CONNECTION;
							return connect(schemas);
						} finally { stateLock.writeLock().unlock(); }
					});
				}

				case NEGOTIATING -> {
					assert connectFuture != null;

					// Wait for the old negotiation to complete
					connectFuture = connectFuture.thenCompose(v -> connect(schemas));
					yield connectFuture;
				}
			};
		} finally { stateLock.writeLock().unlock(); }
	}

	protected final CompletableFuture<Void> closeConnection(boolean wasClosedByOtherEnd) {
		try { stateLock.writeLock().lock();
			return switch (state) {
				case NO_CONNECTION -> {
					state = State.CLOSED;

					failAllOfThePackets(new NetworkException("The connection was closed prior to connecting.", this));
					postCloseActions();

					yield closeFuture = CompletableFuture.completedFuture(null);
				}

				case READY, NEGOTIATING -> {
					closeFuture = closeFuture(wasClosedByOtherEnd);

					state = State.CLOSED;
					yield closeFuture = closeFuture.thenRun(this::postCloseActions);
				}

				case CLOSED -> closeFuture;
			};
		} finally { stateLock.writeLock().unlock(); }
	}

	/**
	 * This method is called by the closing process to return a future that represents any waits that need to occur before the closing process can finish.
	 * @param closedByOtherEnd whether the other end of the connection initiated this close
	 * @return a future to wait on
	 */
	protected CompletableFuture<Void> closeFuture(boolean closedByOtherEnd) {
		if (closedByOtherEnd) {
			// At this point any packets in the send-buffer won't be sent. Fail them all
			failAllOfThePackets(new NetworkException("The other end of the connection was closed before clearing the send-buffer", this));
			return CompletableFuture.completedFuture(null);
		} else {
			// Flush the packets in the send-buffer, then send the close packet
			return CompletableFuture.allOf(flush(), send(new CloseConnectionPacket()));
		}
	}

	/**
	 * This method is called by the closing process after the closing waits, and should be used for any non-blocking closing operations.
	 */
	protected void postCloseActions() {
		/* Take no action */
	}

	@Override
	public final CompletableFuture<Void> closeConnection() {
		return closeConnection(false);
	}

	@Override
	public Protocol protocol() {
		return protocol;
	}

	/****************** PACKET RECEIVE METHODS ********************/

	protected void receiveConfirmPacket(Protocol protocol) {
		try { stateLock.writeLock().lock();
			switch (state) {
				default:
					throw new NetworkException("Unrequested protocol confirmation packet", this);
				case NEGOTIATING:
					// If we were negotiating set the state to ready.
					state = State.READY;
					// FALL THROUGH
				case CLOSED:
					// If we were closed, don't change the state, but send the packets in the send buffer, and notify the
					// connect future.
					if (confirmReceivedFuture == null || confirmReceivedFuture.isDone()) {
						// We did not ask for this packet.
						throw new NetworkException("Unrequested protocol confirmation packet", this);
					}

					confirmReceivedFuture.complete(null);
					this.protocol = protocol;

					// Send all of the packets
					sendAllOfThePackets();
			}
		} finally { stateLock.writeLock().unlock(); }
	}

	private void sendAllOfThePackets() {
		for (var queued : sendBuffer) {
			sendWithoutStateChecks(queued.packet).thenAccept(queued.future::complete);
		}

		sendBuffer.clear();
	}

	private void failAllOfThePackets(NetworkException exception) {
		for (var queued : sendBuffer) {
			queued.future.completeExceptionally(exception);
		}

		sendBuffer.clear();
	}

	protected void receiveNegotiatePacket(Protocol protocol) {
		try { stateLock.writeLock().lock();
			switch (state) {
				case NO_CONNECTION -> {
					state = State.READY;
					this.protocol = protocol;
					sendAllOfThePackets();
				}
				case READY -> this.protocol = protocol;
				case NEGOTIATING -> throw new NetworkException("Incoming negotiation while negotiating.", this);
				case CLOSED -> throw new NetworkException("This connection is closed.", this);
			}
		} finally { stateLock.writeLock().unlock(); }
	}

	/** This method closes the connection without sending a packet to the other connection. */
	protected void receiveClosePacket() {
		closeConnection(true);
	}
}
