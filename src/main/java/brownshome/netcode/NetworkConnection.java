package brownshome.netcode;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import brownshome.netcode.util.PacketSendQueue;

public abstract class NetworkConnection<ADDRESS, CONNECTION_MANAGER extends ConnectionManager<ADDRESS, ?>> extends Connection<ADDRESS, CONNECTION_MANAGER> {
	private static final System.Logger LOGGER = System.getLogger(NetworkConnection.class.getModule().getName());

	private final PacketSendQueue sendQueue;

	/**
	 * Completed after the initial connection is made
	 */
	private final CompletableFuture<Void> readyToSend;

	private volatile CompletableFuture<Void> confirmReceivedFuture;
	private volatile CompletableFuture<Void> closeFuture;

	/**
	 * Creates a connection.
	 */
	protected NetworkConnection(CONNECTION_MANAGER connectionManager, ADDRESS address, Protocol baseProtocol) {
		super(connectionManager, address, baseProtocol);

		readyToSend = new CompletableFuture<>();
		confirmReceivedFuture = CompletableFuture.completedFuture(null);

		sendQueue = new PacketSendQueue(this);
		sendQueue.wait(readyToSend);
	}

	@Override
	public CompletableFuture<Void> send(Packet packet) {
		if (closeFuture != null) {
			CompletableFuture.failedFuture(new NetworkException("This connection is closed.", this));
		}

		return sendQueue.send(packet);
	}

	public record SendResult(CompletableFuture<Void> sent, CompletableFuture<Void> received) { }
	/**
	 * This is the method that is used to send packets internally. Override this.
	 * @see #send(Packet)
	 */
	public abstract SendResult queueForSending(Packet packet, int id, BitSet bitSet);

	@Override
	public CompletableFuture<Void> flush() {
		return sendQueue.flush();
	}

	@Override
	public synchronized CompletableFuture<Void> connect(List<Schema> schemas) {
		if (closeFuture != null) {
			CompletableFuture.failedFuture(new NetworkException("This connection is closed.", this));
		}

		if (readyToSend.isDone()) {
			// This is a reconnection
			return sendQueue.barrier(start -> start.thenCompose(unused -> {
				confirmReceivedFuture = new CompletableFuture<>();
				var result = sendQueue.sendImmediately(new NegotiateProtocolPacket(schemas));
				return CompletableFuture.allOf(result.sent, result.received, confirmReceivedFuture);
			}));
		} else {
			// Initial connection
			confirmReceivedFuture = readyToSend;
			var result = sendQueue.sendImmediately(new NegotiateProtocolPacket(schemas));
			return CompletableFuture.allOf(result.sent, result.received, confirmReceivedFuture);
		}
	}

	@Override
	public final CompletableFuture<Void> closeConnection() {
		return closeConnection(false);
	}

	protected synchronized CompletableFuture<Void> closeConnection(boolean wasClosedByOtherEnd) {
		if (closeFuture != null) {
			return closeFuture;
		}

		// Closed by the other end, just die
		if (wasClosedByOtherEnd) {
			return closeFuture = CompletableFuture.completedFuture(null);
		}

		// From this point on no more packets will be sent
		closeFuture = new CompletableFuture<>();
		return sendQueue.flush().thenCompose(unused -> sendQueue.sendImmediately(new CloseConnectionPacket()).received);
	}

	/****************** PACKET RECEIVE METHODS ********************/

	protected void receiveConfirmPacket(Protocol protocol) {
		if (confirmReceivedFuture.complete(null)) {
			protocol(protocol);
		} else {
			// We did not ask for this packet.
			throw new NetworkException("Unrequested protocol confirmation packet", this);
		}
	}

	protected void receiveNegotiationFailedPacket(String reason) {
		if (confirmReceivedFuture.completeExceptionally(new FailedNegotiationException(reason))) {
			LOGGER.log(System.Logger.Level.ERROR, "Error negotiating schema with ''{0}'': {1}", address(), reason);
		} else {
			// We did not ask for this packet.
			throw new NetworkException("Unrequested protocol confirmation packet", this);
		}
	}

	protected void receiveNegotiatePacket(Protocol.ProtocolNegotiation negotiation) {
		if (negotiation.succeeded()) {
			sendQueue.barrier(start -> start.thenRun(() -> {
				// Within the barrier, the future cannot revert to not done
				if (!confirmReceivedFuture.isDone()) {
					sendQueue.sendImmediately(new NegotiationFailedPacket("Already negotiating"));
				} else {
					// Negotiation passed
					protocol(negotiation.protocol());
				}
			}));
		} else {
			// No barrier needed here, this can occur after the negotiation is complete if there is one
			send(new NegotiationFailedPacket("Missing schema: [%s]".formatted(negotiation.missingSchema()
					.stream()
					.map(Objects::toString)
					.collect(Collectors.joining(", ")))));
		}
	}

	/** This method closes the connection without sending a packet to the other connection. */
	protected void receiveClosePacket() {
		closeConnection(true);
	}
}
