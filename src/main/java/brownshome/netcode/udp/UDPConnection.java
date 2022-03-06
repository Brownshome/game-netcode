package brownshome.netcode.udp;

import brownshome.netcode.*;
import brownshome.netcode.NetworkConnection;
import brownshome.netcode.util.PacketQueue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class UDPConnection extends NetworkConnection<InetSocketAddress, UDPConnectionManager> {
	private static final System.Logger LOGGER = System.getLogger(UDPConnection.class.getModule().getName());

	private static final SecureRandom SALT_PROVIDER = new SecureRandom();
	private static final Protocol UDP_PROTOCOL = new Protocol(List.of(new UdpSchema()));

	/**
	 * The salt used by this connection object
	 **/
	private final long localSalt;

	/**
	 * The salt used by the remote connection object
	 **/
	private final CompletableFuture<Long> remoteSalt;
	private boolean startedConnection = false;

	private final class UDPPacketDispatcher extends PacketDispatcher {
		UDPPacketDispatcher() {
			super(Duration.ofMillis(200), 1024, remoteSalt);
		}

		@Override
		Protocol protocol() {
			return UDPConnection.this.protocol();
		}

		@Override
		Protocol udpLayerProtocol() {
			return UDP_PROTOCOL;
		}

		@Override
		void sendBuffer(ByteBuffer buffer) throws IOException {
			connectionManager().channel().send(buffer, address());
		}
	}
	private final PacketDispatcher packetDispatcher;

	public UDPConnection(UDPConnectionManager manager, InetSocketAddress other) {
		super(manager,
				other,
				// We need the UDP schema as well as the base schema
				new Protocol(List.of(new BaseSchema(0), new UdpSchema(0))));

		localSalt = SALT_PROVIDER.nextLong();
		remoteSalt = new CompletableFuture<>();

		packetDispatcher = new UDPPacketDispatcher();
	}

	@Override
	public SendResult queueForSending(Packet packet, int id, BitSet bitSet) {
		return packetDispatcher.queuePacket(Instant.now(), packet, id, bitSet);
	}

	@Override
	public synchronized CompletableFuture<Void> connect(List<Schema> schemas) {
		if (!startedConnection) {
			startedConnection = true;
			packetDispatcher.sendConnectPacket(localSalt);
		}

		return super.connect(schemas);
	}

	/* *********** CALLBACKS FROM PACKET RECEIVE *********** */

	long localSalt() {
		return localSalt;
	}

	void receive(ByteBuffer buffer) {
		Packet incoming = UDP_PROTOCOL.createPacket(buffer);
		LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Remote address '%s' sent '%s'", address(), incoming));
		connectionManager().executorService(incoming.getClass()).execute(() -> UDP_PROTOCOL.handle(this, incoming));
	}

	synchronized void receiveConnectPacket(long clientSalt) {
		try {
			if (startedConnection || remoteSalt.isCompletedExceptionally()) {
				packetDispatcher.sendUdpPacket(new ConnectionDeniedPacket(
						UDPPackets.hashConnectionDeniedPacket(clientSalt)));
			} else if (!remoteSalt.complete(clientSalt) && remoteSalt.get() == clientSalt) {
				// Resend the challenge packet
				packetDispatcher.sendUdpPacket(new ChallengePacket(
						UDPPackets.hashChallengePacket(clientSalt, localSalt),
						localSalt));
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new AssertionError(e);
		}
	}

	synchronized void connectionDenied() {
		if (startedConnection) {
			remoteSalt.completeExceptionally(new NetworkException("The connection was denied", this));
		}
	}

	synchronized void receiveChallengeSalt(long serverSalt) {
		if (startedConnection) {
			remoteSalt.complete(serverSalt);
		}
	}

	boolean onSequenceNumberReceived(int sequenceNumber, boolean queueAcknowledgement) {
		return packetDispatcher.onSequenceNumberReceived(sequenceNumber, queueAcknowledgement);
	}

	/**
	 * Called when an acknowledgement is received, this should be used to communicate completed transmission.
	 **/
	void receiveAcknowledgement(int sequenceNumber) {
		packetDispatcher.onAcknowledgementReceived(sequenceNumber);
	}

	private final UdpPacketExecutor executor = new UdpPacketExecutor(this);
	public void receiveMessages(int sequenceNumber, int olderRequiredPackets, ByteBuffer messages) {
		while (messages.hasRemaining()) {
			var packet = protocol().createPacket(messages);
			executor.execute(sequenceNumber, olderRequiredPackets, packet);
		}
	}
}
