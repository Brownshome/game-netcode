package brownshome.netcode.udp;

import brownshome.netcode.*;
import brownshome.netcode.NetworkConnection;
import brownshome.netcode.ordering.OrderingManager;
import brownshome.netcode.ordering.SequencedPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class UDPConnection extends NetworkConnection<InetSocketAddress> {
	/*

	The subclass handles the connection and protocol negotiation. We need to decide how to identify and secure clients.
	We also need to fragment packets, and other such things.

	First we need to send a challenge and acceptance system.

	 */

	/** The RNG used to produce salts, this may not be thread-safe, so it is protected by a synchronized block on itself */
	private static final SecureRandom SALT_PROVIDER = new SecureRandom();

	private static final System.Logger LOGGER = System.getLogger(UDPConnection.class.getModule().getName());
	private static final long CONNECT_RESEND_DELAY_MS = 100;
	private static final int FRAGMENT_SIZE = 1024;

	private static final Protocol UDP_LAYER_PROTOCOL = new Protocol(List.of(new UDPSchema()));

	private final UDPConnectionManager manager;

	/** The salt used by this connection object */
	private final long localSalt;
	/** The salt used by the remote connection object */
	private long remoteSalt;

	private final OrderingManager orderingManager;

	private final MessageScheduler messageScheduler;

	private final CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
	private final AtomicBoolean startedConnection = new AtomicBoolean(false);

	/** Represents a set of fragments that are being received. */
	private final static class FragmentSet {
		final BitSet receivedPackets = new BitSet();
		int receivedCount = 0;

		byte[] data = null;

		/** This is -1 if the length of the packet is as yet unknown */
		int length = -1;

		/** The sequence number of fragment 0 */
		int firstPacketNumber = -1;

		/** Returns true if all of the fragments have been received. */
		boolean done() {
			if(length == -1)
				return false;

			// +1 because there always needs to be a fragment with size != FRAGMENT_SIZE
			return receivedCount == length / FRAGMENT_SIZE + 1;
		}

		void receivedFragment(int sequenceNumber, int fragmentNo, ByteBuffer buffer) {
			if (receivedPackets.get(fragmentNo)) {
				return; //This data has already been received, not sure if this is possible??
			}

			if (fragmentNo == 0) {
				firstPacketNumber = sequenceNumber;
			}

			receivedCount++;
			receivedPackets.set(fragmentNo);

			if (buffer.remaining() < FRAGMENT_SIZE) {
				// This is the last fragment
				length = fragmentNo * FRAGMENT_SIZE + buffer.remaining();

				if (data == null) {
					data = new byte[length];
				} else if(data.length < length) {
					data = Arrays.copyOf(data, length);
				}
			}

			if (data == null) {
				// This gives a conservative estimate of 32k for the fragmented packet
				data = new byte[Math.max((fragmentNo + 1) * 2, 32) * FRAGMENT_SIZE];
			} else if (length == -1 && data.length < (fragmentNo + 1) * FRAGMENT_SIZE) {
				// We don't know the length and the buffer is probably too small, resize it to double the size.
				data = Arrays.copyOf(data, data.length * 2);
			}

			// Data exists now, and is an appropriate size, copy it into the master buffer
			buffer.get(data, fragmentNo * FRAGMENT_SIZE, buffer.remaining());
		}

		/**
		 * This must only be called when done() returns true
		 */
		ByteBuffer createDataBuffer() {
			assert done();

			return ByteBuffer.wrap(data, 0, length);
		}

		/**
		 * Returns the sequence number that should be associated with this packet. This is the sequence number of the
		 * first fragment.
		 *
		 * This must only be called when done() is true
		 */
		int sequenceNumber() {
			assert done();

			return firstPacketNumber;
		}
	}

	private final Map<Integer, FragmentSet> fragmentSets = new HashMap<>();

	public UDPConnection(UDPConnectionManager manager, InetSocketAddress other) {
		super(other);

		this.orderingManager = new OrderingManager(this::execute, this::drop);
		this.manager = manager;
		this.messageScheduler = new MessageScheduler(this);

		synchronized (SALT_PROVIDER) {
			localSalt = SALT_PROVIDER.nextLong();
		}
	}

	/**
	 * This method returns the default set of protocols that are used to communicate before the connection is negotiated
	 */
	@Override
	protected Protocol baseProtocol() {
		// We need the UDP schema as well as the base schema
		return new Protocol(List.of(new BaseSchema(0), new UDPSchema(0)));
	}

	/**
	 * Called by the ordering system when this incoming packet can be executed.
	 */
	private synchronized void execute(Packet packet) {
		manager.executeOn(() -> {
			try {
				protocol().handle(this, packet);
			} catch (NetworkException ne) {
				send(new ErrorPacket(ne.getMessage()));
			}

			orderingManager.notifyExecutionFinished(packet);
		}, packet.handledBy());
	}

	/**
	 * This is called by the execution system when a packet cannot be executed due to ordering constraints.
	 **/
	private void drop(Packet packet) {
		String dropMsg = "Packet '%s' was dropped due to ordering constraints".formatted(packet);

		LOGGER.log(System.Logger.Level.WARNING, dropMsg);

		if (packet.reliable()) {
			send(new ErrorPacket(dropMsg));
		}
	}

	@Override
	public CompletableFuture<Void> connect(List<Schema> schemas) {
		// We sent the connect-packet until we get a response.

		// As the message resend system is not yet useful as there are no acks, we pick a sensible resend delay.

		if (!startedConnection.getAndSet(true) && !connectedFuture.isDone()) {
			ConnectPacket packet = new ConnectPacket(localSalt, null);

			ByteBuffer buffer = ByteBuffer.allocate(calculateEncodedLength(packet));
			encodeUDP(buffer, packet);
			buffer.flip();

			ScheduledFuture<?> scheduledFuture = manager.submissionThread().scheduleAtFixedRate(new Runnable() {
				int attempts = 0;

				@Override
				public void run() {
					try {
						try {
							manager.channel().send(buffer.duplicate(), address());
							flagPacketSend(packet);
						} catch(IOException e) {
							connectedFuture.completeExceptionally(e);
						}

						attempts++;

						// attempts > msToWait / CONNECT_RESEND_DELAY
						long msToWait = 10_000;
						if (attempts > msToWait / CONNECT_RESEND_DELAY_MS) {
							connectedFuture.completeExceptionally(new TimeoutException("Connection to '" + address() + "' failed to reply in " + msToWait + "ms."));
						}
					} catch (Throwable t) {
						LOGGER.log(System.Logger.Level.ERROR, "Uncaught exception in message scheduler", t);
					}
				}
			}, 0, CONNECT_RESEND_DELAY_MS, TimeUnit.MILLISECONDS);

			// This also triggers on cancels and exceptional failures
			connectedFuture.whenComplete((v, t) -> scheduledFuture.cancel(false));
		}

		return connectedFuture.thenCompose(v -> super.connect(schemas));
	}

	@Override
	protected CompletableFuture<Void> sendWithoutStateChecks(Packet packet) {
		//Place the buffer into the message queue
		return messageScheduler.schedulePacket(packet);
	}

	@Override
	public UDPConnectionManager connectionManager() {
		return manager;
	}

	/* ***********    PACKET ENCODING SYSTEMS    *********** */

	// TODO intelligent packet ID compression (Byte Short ect)

	int calculateEncodedLength(Packet packet) {
		return packet.size() + Integer.BYTES;
	}

	void encode(ByteBuffer buffer, Packet packet) {
		assert !packet.schemaName().equals(UDPSchema.FULL_NAME);

		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
	}

	void encodeUDP(ByteBuffer buffer, Packet packet) {
		assert packet.schemaName().equals(UDPSchema.FULL_NAME);

		buffer.putInt(UDP_LAYER_PROTOCOL.computePacketID(packet));
		packet.write(buffer);
	}

	/* *********** OVERRIDE CLOSE METHOD ************* */

	@Override
	protected CompletableFuture<Void> closeFuture(boolean closedByOtherEnd) {
		return CompletableFuture.allOf(super.closeFuture(closedByOtherEnd), messageScheduler.closeMessageSchedulerFuture());
	}

	/* *********** CALLBACKS FROM PACKET RECEIVE *********** */

	void receive(ByteBuffer buffer) {
		Packet incoming = UDP_LAYER_PROTOCOL.createPacket(buffer);
		if (!incoming.schemaName().equals(UDPSchema.FULL_NAME)) {
			LOGGER.log(System.Logger.Level.ERROR, String.format("Remote address '%s' sent non-UDP packet '%s'", address(), incoming));
		} else {
			LOGGER.log(System.Logger.Level.DEBUG, () -> String.format("Remote address '%s' sent '%s'", address(), incoming));
		}

		manager.executeOn(() -> {
			UDP_LAYER_PROTOCOL.handle(this, incoming);
		}, incoming.handledBy());
	}

	void receiveConnectPacket(long clientSalt) {
		Packet packet;
		if (!startedConnection.getAndSet(true)) {
			connectedFuture.complete(null);
			remoteSalt = clientSalt;

			int hash = UDPPackets.hashChallengePacket(remoteSalt, localSalt());
			packet = new ChallengePacket(hash, localSalt());
		} else {
			int hash = UDPPackets.hashConnectionDeniedPacket(clientSalt);
			packet = new ConnectionDeniedPacket(hash);
		}

		ByteBuffer buffer = ByteBuffer.allocate(calculateEncodedLength(packet));
		encodeUDP(buffer, packet);
		buffer.flip();

		try {
			manager.channel().send(buffer, address());
			flagPacketSend(packet);
		} catch (IOException io) {
			throw new NetworkException("Unable to send challenge packet to " + address(), this);
		}
	}

	private void flagPacketSend(Packet packet) {
		LOGGER.log(System.Logger.Level.DEBUG, "Sending ''{0}'' to ''{1}''", packet, address());
	}

	long localSalt() {
		return localSalt;
	}

	long remoteSalt() {
		return remoteSalt;
	}

	void connectionDenied() {
		connectedFuture.completeExceptionally(new NetworkException("The connection was denied", this));
	}

	void receiveChallengeSalt(long serverSalt) {
		remoteSalt = serverSalt;
		connectedFuture.complete(null);
	}

	private void receiveMessage(int packetNumber, int messageNumber, ByteBuffer data) {
		Packet incoming = protocol().createPacket(data);

		LOGGER.log(System.Logger.Level.INFO, () -> String.format("Remote address '%s' sent '%s'", address(), incoming.toString()));

		// Dispatch the packet to the execution queue.
		// TODO threadsafe
		orderingManager.deliverPacket(new SequencedPacket(incoming, packetNumber * MessageScheduler.MAXIMUM_MESSAGES_PER_PACKET + messageNumber));
	}

	void receiveBlockOfMessages(int sequenceNumber, ByteBuffer messages) {
		int messageNumber = 0;
		while (messages.hasRemaining()) {
			receiveMessage(sequenceNumber, messageNumber++, messages);
		}
	}

	/**
	 * Called by the networking system when a fragment packet is received.
	 * @param fragmentSet the set of fragments that this fragment belongs to.
	 * @param fragmentNo the number of this fragment.
	 * @param fragmentData the data that arrived. If this data is smaller than FRAGMENT_SIZE then it is the last fragment.
	 */
	void receiveFragment(int packetSequenceNumber, int fragmentSet, int fragmentNo, ByteBuffer fragmentData) {
		assert fragmentSet >= 0;
		assert fragmentNo >= 0;

		if (fragmentData.remaining() > FRAGMENT_SIZE) {
			throw new IllegalArgumentException("Fragment buffer too large");
		}

		FragmentSet set = fragmentSets.computeIfAbsent(fragmentNo, unused -> new FragmentSet());
		set.receivedFragment(packetSequenceNumber, fragmentNo, fragmentData);

		if (set.done()) {
			receiveMessage(set.sequenceNumber(), 0, set.createDataBuffer());
			fragmentSets.remove(fragmentSet);
		}
	}

	/**
	 * Called when an ack is received, this should be used to communicate completed transmission.
	 **/
	void receiveAcks(Ack ack) {
		messageScheduler.ackReceived(ack);
	}

	// TODO clear old acks for wrap-around.
	private final Set<Integer> receivedAcks = new HashSet<>();

	/**
	 * Returns true if the sequence number should be accepted, or false if rejected.
	 **/
	boolean receiveSequenceNumber(int sequenceNumber) {
		messageScheduler.receiveSequenceNumber(sequenceNumber);

		return receivedAcks.add(sequenceNumber);
	}
}
