package brownshome.netcode;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public abstract class Connection<ADDRESS> implements Closeable {
	private static final Logger LOGGER = Logger.getLogger("network");

	public enum State {
		/** The connection is not yet connected. Packets will be sent when the connection is ready. */
		NO_CONNECTION,

		/** The connection is negotiating a protocol. Packets will be sent when this protocol is negotiated. */
		NEGOTIATING,

		/** The connection is ready to send packets. */
		READY,

		/** The connection is closed. Any attempt to send packets will fail. */
		CLOSED;
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

	/** This class keeps track of what packets have been sent and keeps track of what packets have not yet been sent.
	 * This thread is not a daemon thread by default.
	 *
	 * Packets that error, or are canceled are considered flushed, the future will never error, or cancel itself.
	 **/
	private final class Flusher extends Thread {
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

		Flusher() {
			super(String.format("NETCODE-FLUSHER-%s", address()));

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
					LOGGER.info(String.format("%s flusher dying due to interrupt.", address()));
					return;
				} catch(ExecutionException | CancellationException e) {
					//Do nothing, the packet is considered 'flushed'
				}
			}
		}

		/** Gets a future that returns when all packets sent before this call have been flushed */
		CompletableFuture<Void> flush() {
			QueueItem item = new QueueItem();

			queue.add(item);

			return item.future;
		}

		void submitFuture(CompletableFuture<Void> future) {
			queue.add(new QueueItem(future));
		}
	}

	private final Flusher flusher;

	/**
	 * Creates a connection.
	 */
	protected Connection(ADDRESS address) {
		this.address = address;
		this.state = State.NO_CONNECTION;
		this.stateLock = new ReentrantReadWriteLock();
		this.protocol = Protocol.baseProtocol();
		this.sendBuffer = new ArrayList<>();
		this.flusher = new Flusher();

		flusher.start();
	}

	/**
	 * Sends a packet. If this connection has not yet connected, then the packet will be sent as soon as the connection is made.
	 * @return A future that will return when the packet has been sent. In the case of a reliable packet it will return when it
	 * has been received.
	 */
	public CompletableFuture<Void> send(Packet packet) {
		//The read lock must contain the sending line, as otherwise there might be a packet in the process of sending
		//when the state is changed from READY to NEGOTIATING
		try { stateLock.readLock().lock();
			if(state == State.CLOSED) {
				return CompletableFuture.failedFuture(new NetworkException("The connection is closed.", this));
			}

			if(state == State.READY) {
				CompletableFuture<Void> future = sendWithoutStateChecks(packet);
				flusher.submitFuture(future);
				return future;
			} else {
				CompletableFuture<Void> future = new CompletableFuture<>();
				QueuedPacket queuedPacket = new QueuedPacket(packet, future);
				sendBuffer.add(queuedPacket);
				flusher.submitFuture(future);
				return future;
			}
		} finally { stateLock.readLock().unlock(); }
	}

	/**
	 * This is the method that is used to send packets internally. Override this.
	 * @see #send(Packet)
	 */
	protected abstract CompletableFuture<Void> sendWithoutStateChecks(Packet packet);

	public final void sendSync(Packet packet) throws InterruptedException, NetworkException {
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
	public CompletableFuture<Void> flush() {
		return flusher.flush();
	}

	public final void flushSync() throws InterruptedException, NetworkException {
		awaitFuture(flush());
	}

	/** Gets that address object for this connection. */
	public ADDRESS address() {
		return address;
	}

	/** Attempts to connect to the host at the other end. This method has no effect if the connection has already been
	 * connected.
	 * @return A future that represents when a connection has been made successfully.
	 **/
	public CompletableFuture<Void> connect() {
		//Send a connect packet.

		try { stateLock.writeLock().lock();

			if(state == State.NO_CONNECTION) {
				state = State.NEGOTIATING;

				//Start waiting for the confirmProtocolPacket
				confirmReceivedFuture = new CompletableFuture<>();
				CompletableFuture<Void> negotiateSentPacket = sendWithoutStateChecks(new NegotiateProtocolPacket(connectionManager().schemas()));

				//This is needed to detect sending failures. If we just wait for the received then if the negotiate packet
				//fails this future locks up. The future still may lock up if the confirm fails, but this is a little
				//bit more friendly.

				connectFuture = CompletableFuture.allOf(negotiateSentPacket, confirmReceivedFuture);

				flusher.submitFuture(negotiateSentPacket);
			}
		} finally { stateLock.writeLock().unlock(); }

		return connectFuture;
	}
	
	public final void connectSync() throws InterruptedException, NetworkException {
		awaitFuture(connect());
	}

	/**
	 * Closes the connection. This will send all packets that have been already queued up, and then terminate the connection.
	 * Closing a connection that has already been closed will have no effect.
	 * @return A future that returns when the connection has been closed cleanly.
	 */
	public CompletableFuture<Void> closeConnection(boolean sendPacket) {
		try { stateLock.writeLock().lock();
			switch(state) {
				case NO_CONNECTION:
					//Connect was not called, change the state to CLOSED, any packets that were queued up, will never be
					//sent, as connect can never be called. So terminate them all.
					//Also set the connect method to return instantly to keep to spec.

					connectFuture = CompletableFuture.completedFuture(null);

					state = State.CLOSED;

					for(QueuedPacket packet : sendBuffer) {
						packet.future.completeExceptionally(new NetworkException("The connection was closed prior to connecting.", this));
					}

					closeFuture = CompletableFuture.completedFuture(null);

					//Stop the flusher thread.
					flusher.interrupt();

					return closeFuture;
				case READY:
				case NEGOTIATING:
					CompletableFuture<Void> closeSendFuture = sendPacket
							? send(new CloseConnectionPacket())
							: CompletableFuture.completedFuture(null); //If this future fails, fail the closing future.

					state = State.CLOSED;

					closeFuture = CompletableFuture.allOf(closeSendFuture, flush());

					//Shut down the flusher
					closeFuture.thenRun(flusher::interrupt);

					return closeFuture;
				case CLOSED:
					//Take no action
					return closeFuture;
				default:
					throw new IllegalStateException("State cannot be null");
			}
		} finally { stateLock.writeLock().unlock(); }
	}

	/**
	 * This is for the Closeable interface, this method is identical to awaitCloseConnection.
	 * @throws IOException If any errors occur while closing the connection.
	 */
	@Override
	public final void close() throws IOException {
		try {
			awaitCloseConnection();
		} catch(InterruptedException e) {
			throw new IOException("Closing process interrupted.", e);
		} catch(NetworkException ne) {
			throw new IOException("Network error sending close request", ne);
		}
	}

	public final void awaitCloseConnection() throws InterruptedException, NetworkException {
		awaitFuture(closeConnection(true));
	}

	protected <T> T awaitFuture(Future<T> future) throws InterruptedException, NetworkException {
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
	public abstract ConnectionManager<ADDRESS, ? extends Connection<ADDRESS>> connectionManager();

	/** Returns the protocol that is used by this connection. */
	public Protocol protocol() {
		return protocol;
	}

	/****************** PACKET RECEIVE METHODS ********************/

	protected void receiveConfirmPacket(Protocol protocol) {
		try { stateLock.writeLock().lock();
			switch(state) {
				default:
					throw new NetworkException("Unrequested protocol confirmation packet", this);
				case NEGOTIATING:
					//If we were negotiating set the state to ready.
					state = State.READY;
					//FALL THROUGH
				case CLOSED:
					//If we were closed, don't change the state, but send the packets in the send buffer, and notify the
					//connect future.
					if(confirmReceivedFuture == null || confirmReceivedFuture.isDone()) {
						//We did not ask for this packet.
						throw new NetworkException("Unrequested protocol confirmation packet", this);
					}

					confirmReceivedFuture.complete(null);
					this.protocol = protocol;

					//Send all of the packets
					sendAllOfThePackets();
			}

			state = State.READY;
		} finally { stateLock.writeLock().unlock(); }
	}

	private void sendAllOfThePackets() {
		for(var queued : sendBuffer) {
			sendWithoutStateChecks(queued.packet).thenAccept(queued.future::complete);
		}
	}

	protected void receiveNegotiatePacket(Protocol protocol) {
		try { stateLock.writeLock().lock();
			switch(state) {
				case NO_CONNECTION:
					state = State.READY;
					this.protocol = protocol;

					sendAllOfThePackets();

					break;
				case READY:
					this.protocol = protocol;
					break;
				case NEGOTIATING:
					throw new NetworkException("Incoming negotiation while negotiating.", this);
				case CLOSED:
					throw new NetworkException("This connection is closed.", this);
			}
		} finally { stateLock.writeLock().unlock(); }
	}
}
