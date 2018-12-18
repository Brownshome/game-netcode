package brownshome.netcode.udp;

import brownshome.netcode.NetworkConnection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class UDPConnection extends NetworkConnection<InetSocketAddress> {
	/*

	The subclass handles the connection and protocol negotiation. We need to decide how to identify and secure clients.
	We also need to fragment packets, and other such things.

	First we need to send a challenge and acceptance system.

	 */

	/** The RNG used to produce salts, this may not be threadsafe, so it is protected by a synchronized block on itself */
	private static final SecureRandom SALT_PROVIDER = new SecureRandom();

	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");
	private static final long CONNECT_RESEND_DELAY_MS = 100;

	private final UDPConnectionManager manager;

	private long localSalt, remoteSalt;

	private CompletableFuture<Void> udpConnectionResponse;

	public UDPConnection(UDPConnectionManager manager, InetSocketAddress other) {
		super(other);

		this.manager = manager;
	}

	@Override
	public CompletableFuture<Void> connect() {
		// We sent the connect packet until we get a response.

		// As the message resend system is not yet useful as there are no acks, we pick a sensible resend delay.

		synchronized(SALT_PROVIDER) {
			localSalt = SALT_PROVIDER.nextLong();
		}

		ConnectPacket packet = new ConnectPacket(localSalt, null);

		ByteBuffer buffer = ByteBuffer.allocate(packet.size() + Integer.BYTES);
		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
		buffer.flip();

		CompletableFuture<Void> udpConnectionResponse = new CompletableFuture<>();

		ScheduledFuture<?> scheduledFuture = manager.submissionThread().scheduleAtFixedRate(new Runnable() {
			int attempts = 0;

			@Override
			public void run() {
				try {
					manager.channel().send(buffer.duplicate(), address());
				} catch(IOException e) {
					udpConnectionResponse.completeExceptionally(e);
				}

				attempts++;

				// attempts > msToWait / CONNECT_RESEND_DELAY
				long msToWait = 10_000;
				if(attempts > msToWait / CONNECT_RESEND_DELAY_MS) {
					udpConnectionResponse.completeExceptionally(new TimeoutException("Connection to '" + address() + "' failed to reply in " + msToWait + "ms."));
				}
			}
		}, 0, CONNECT_RESEND_DELAY_MS, TimeUnit.MILLISECONDS);

		//This also triggers on cancels and exceptional failures
		udpConnectionResponse.whenComplete((v, t) -> scheduledFuture.cancel(false));

		return udpConnectionResponse.thenCombine(super.connect(), (a, b) -> null);
	}

	@Override
	public CompletableFuture<Void> sendWithoutStateChecks(Packet packet) {
		ByteBuffer buffer = ByteBuffer.allocate(packet.size() + Integer.BYTES);
		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
		buffer.flip();

		//Place the buffer into the message queue

		return CompletableFuture.completedFuture(null);
	}

	@Override
	public UDPConnectionManager connectionManager() {
		return manager;
	}

	protected void receive(ByteBuffer buffer) {
		Packet incoming = protocol().createPacket(buffer);

		LOGGER.info(() -> String.format("Address '%s' received '%s'", address(), incoming.toString()));

		manager.executeOn(() -> {
			protocol().handle(this, incoming);
		}, incoming.handledBy());
	}

	public void receiveConnectPacket(long clientSalt) {
		remoteSalt = clientSalt;
	}

	public long localSalt() {
		return localSalt;
	}

	public void connectionDenied() {
		udpConnectionResponse.completeExceptionally(new NetworkException("The connection was denied", this));
	}

	public void receiveChallengeSalt(long serverSalt) {
		remoteSalt = serverSalt;
		udpConnectionResponse.complete(null);
	}

	public void receiveBlockOfMessages(ByteBuffer messages) {

	}
}
