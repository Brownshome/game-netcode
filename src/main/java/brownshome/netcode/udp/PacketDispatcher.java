package brownshome.netcode.udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import brownshome.netcode.*;

/**
 * This is a helper class for the UDP subsystem that handles the dispatching of messages. This class assembles packets
 * of size MTU out of the messages currently waiting to be sent in the buffer. It has several goals:
 *
 * <ul>
 *     <li>Resend reliable packets</li>
 *     <li>Annotate each packet with ordering information</li>
 *     <li>Construct packets from supplied messages</li>
 * </ul>
 *
 * To accomplish this the class will keep track of the approximate bandwidth of the connection and accumulate bits.
 * A minimum delay between packets will be introduced to ensure that bandwidth is not wasted on connection overheads.
 **/
abstract class PacketDispatcher {
	private static final System.Logger LOGGER = System.getLogger(PacketDispatcher.class.getModule().getName());

	private final int maximumPayloadSize;
	private final CompletableFuture<Long> remoteSalt;
	private final Duration resendDelay;

	private interface PrioritisedPacket extends Comparable<PrioritisedPacket> {
		int priority();
		Instant queueTime();

		@Override
		default int compareTo(PrioritisedPacket p) {
			int i = priority() - p.priority();

			if (i == 0) {
				return queueTime().compareTo(p.queueTime());
			}

			return i;
		}
	}

	/**
	 * The priority of a packet depends on its submission time and its priority. Priority takes precedence. Packets that
	 * have been sent are recycled into the priority queue after RESEND_WAIT has expired. After this point they should have been
	 * received and will be resent.
	 */
	private record QueuedPacket(Instant queueTime, Packet packet, int type, BitSet waitsFor, NetworkConnection.SendResult futures) implements PrioritisedPacket {
		@Override
		public int priority() {
			return packet.priority();
		}
	}
	private final BlockingQueue<QueuedPacket> queue = new PriorityBlockingQueue<>();

	private record SentPacket(Instant queueTime, int priority, ByteBuffer encodedPacket, NetworkConnection.SendResult futures) implements PrioritisedPacket { }
	private final BlockingQueue<SentPacket> resendQueue = new PriorityBlockingQueue<>();

	private final SequenceNumberPool waitingAcks = new SequenceNumberPool();
	private final AcknowledgementSender ackSender = new AcknowledgementSender();

	/**
	 * A list of sequence numbers that contain packets of a given type. Packets that have been received have been omitted.
	 *
	 * Bit n in the bitfield represents newestSequenceNumber - n + 1
	 */
	private record InFlightTypes(int newestSequenceNumber, int bitfield, int numberOfOlderNumbers) {
		InFlightTypes {
			assert numberOfOlderNumbers >= 0;
		}

		InFlightTypes() {
			this(-1, 0, 0);
		}

		InFlightTypes addPacket(int sequenceNumber) {
			assert sequenceNumber > newestSequenceNumber;

			int newBitField = bitfield << sequenceNumber - newestSequenceNumber | 1;
			return new InFlightTypes(sequenceNumber,
					newBitField,
					numberOfOlderNumbers + Integer.bitCount(bitfield) - Integer.bitCount(newBitField) + 1);
		}

		InFlightTypes addAcknowledgementReceived(int sequenceNumber) {
			assert sequenceNumber <= newestSequenceNumber;

			int bit = 1 << newestSequenceNumber - sequenceNumber;

			if (bit == 0) {
				// Too old
				return new InFlightTypes(newestSequenceNumber, bitfield, numberOfOlderNumbers - 1);
			} else {
				return new InFlightTypes(newestSequenceNumber, bitfield & ~bit, numberOfOlderNumbers);
			}
		}

		/**
		 * Returns the sequence numbers in this wait-set encoded relative to the given sequence number
		 * @param sequenceNumber the sequence number
		 * @return a 32-bit number representing the sequence numbers in this type, or -1 if there are packets that are too
		 *         old to encode that have not been acknowledged.
		 */
		long makeRequiredPacketsField(int sequenceNumber) {
			if (numberOfOlderNumbers > 0) {
				return -1;
			}

			assert sequenceNumber > newestSequenceNumber;
			int encoded = bitfield << sequenceNumber - newestSequenceNumber - 1;

			if (Integer.bitCount(encoded) != Integer.bitCount(bitfield)) {
				return -1;
			} else {
				return Integer.toUnsignedLong(encoded);
			}
		}
	}
	private final List<AtomicReference<InFlightTypes>> inFlightTypes;

	private final AtomicBoolean nextPacketConstructed = new AtomicBoolean(false);

	PacketDispatcher(Duration resendDelay,
	                 int maximumPayloadSize,
	                 CompletableFuture<Long> remoteSalt) {
		this.resendDelay = resendDelay;
		this.maximumPayloadSize = maximumPayloadSize;
		this.remoteSalt = remoteSalt;

		inFlightTypes = new CopyOnWriteArrayList<>();
	}

	void encode(ByteBuffer buffer, Packet packet) {
		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
	}

	private void encodeUdpLayer(ByteBuffer buffer, Packet packet) {
		buffer.putInt(udpLayerProtocol().computePacketID(packet));
		packet.write(buffer);
	}

	int encodedSize(Packet packet) {
		return packet.size() + Integer.BYTES;
	}

	abstract Protocol protocol();
	abstract Protocol udpLayerProtocol();

	abstract void sendBuffer(ByteBuffer buffer) throws IOException;

	/**
	 * Queues a packet for sending
	 * @param queueTime the time that the packet was queued, this is used for prioritising packets.
	 * @param packet the packet to sent
	 * @param type a numerical ID for the packet type
	 * @param waitsFor the packet types to wait for; this is the same ID used in type
	 * @return a pair of futures. Note, if the packet is not reliable then the received future will be null
	 */
	NetworkConnection.SendResult queuePacket(Instant queueTime, Packet packet, int type, BitSet waitsFor) {
		NetworkConnection.SendResult futures = packet.reliable()
				? NetworkConnection.SendResult.newReliable()
				: NetworkConnection.SendResult.newUnreliable();

		queue.add(new QueuedPacket(queueTime, packet, type, waitsFor, futures));

		if (nextPacketConstructed.compareAndSet(false, true)) {
			constructNextPacket();
		}

		return futures;
	}

	void sendConnectPacket(long localSalt) {
		var packet = new ConnectPacket(localSalt, null);

		var buffer = ByteBuffer.allocate(encodedSize(packet)).order(ByteOrder.LITTLE_ENDIAN);
		encode(buffer, packet);
		buffer.flip();


	}

	void sendUdpPacket(Packet packet) {
		var buffer = ByteBuffer.allocate(encodedSize(packet)).order(ByteOrder.LITTLE_ENDIAN);
		encode(buffer, packet);
		buffer.flip();
	}

	boolean onSequenceNumberReceived(int sequenceNumber, boolean queueAcknowledgement) {
		boolean isNew = ackSender.onSequenceNumberReceived(sequenceNumber, queueAcknowledgement);

		if (isNew && queueAcknowledgement) {
			constructNextPacket();
		}

		return isNew;
	}

	void onAcknowledgementReceived(int sequenceNumber) {
		waitingAcks.onAcknowledgementReceived(sequenceNumber);
	}

	private long computeWaitForValue(BitSet waits, int sequenceNumber) {
		long result = 0;

		// Iterate set bits in the wait set
		for (int i = waits.nextSetBit(0); i >= 0; i = waits.nextSetBit(i + 1)) {
			if (inFlightTypes.size() <= i) {
				break;
			}

			long requiredPackets = inFlightTypes.get(i).get().makeRequiredPacketsField(sequenceNumber);

			if (requiredPackets == -1) {
				return -1;
			}

			result |= requiredPackets;
		}

		return result;
	}

	/**
	 * Constructs a packet and queues it for sending after enough bandwidth has been accrued
	 */
	private void constructNextPacket() {
		/*
		 * 1. Check the resend queue for packets
		 * 2. Check the queue for packets
		 * 3. Send
		 */

		SentPacket resend = resendQueue.peek();

		QueuedPacket queued;
		if (resend != null && ((queued = queue.peek()) == null || resend.compareTo(queued) >= 0)) {
			// Resend the packet
			resend = resendQueue.poll();
			send(resend.priority, resend.encodedPacket, resend.futures);
		} else {
			// Packets we can't send as there are not enough bits in the ordering constraint field. THIS SHOULD BE RARE
			List<QueuedPacket> invalidPackets = new ArrayList<>();

			var acknowledgement = ackSender.constructAcknowledgementField();
			var futures = NetworkConnection.SendResult.newReliable();
			var messages = ByteBuffer.allocate(maximumPayloadSize).order(ByteOrder.LITTLE_ENDIAN);
			int waitForValues = 0;
			int sequenceNumber = waitingAcks.allocateSequenceNumber(futures.received());
			int priority = -1;

			while ((queued = queue.poll()) != null) {
				/*
				 * Stop building the packet if the next message won't fit. There is no need to cram packets, we'll send
				 * another one soon anyway.
				 */
				if (encodedSize(queued.packet) > messages.remaining()) {
					invalidPackets.add(queued);
					break;
				}

				long waits = computeWaitForValue(queued.waitsFor, sequenceNumber);

				// This packet cannot be added as the wait field is not long enough to store the dependency
				if (waits == -1) {
					invalidPackets.add(queued);
					continue;
				}

				// Add the packet
				waitForValues |= waits;
				encode(messages, queued.packet);

				// Update packet priority
				priority = Math.max(priority, queued.priority());

				// In-flight types
				while (inFlightTypes.size() <= queued.type) {
					inFlightTypes.add(new AtomicReference<>(new InFlightTypes()));
				}

				var inFlightAtomic = inFlightTypes.get(queued.type);
				inFlightAtomic.updateAndGet(inFlight -> inFlight.addPacket(sequenceNumber));

				// Link the futures for this packet
				var packetFutures = queued.futures;
				futures.sent().thenRun(() -> packetFutures.sent().complete(null));

				futures.received().thenRun(() -> {
					if (packetFutures.received() != null) {
						packetFutures.received().complete(null);
					}

					inFlightAtomic.updateAndGet(inFlight -> inFlight.addAcknowledgementReceived(sequenceNumber));
				});
			}

			// Re-add invalid packets
			queue.addAll(invalidPackets);

			long salt;
			try {
				salt = remoteSalt.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new IllegalStateException(e);
			}

			// Encode packet
			messages.flip();
			var hash = UDPPackets.hashDataPacket(
					salt,
					acknowledgement,
					sequenceNumber, waitForValues,
					messages.duplicate());

			var packet = new UdpDataPacket(hash, acknowledgement, sequenceNumber, waitForValues, messages.duplicate());

			var encoded = ByteBuffer.allocate(packet.size()).order(ByteOrder.LITTLE_ENDIAN);
			packet.write(encoded);
			encoded.flip();

			// Queue send
			send(priority, encoded, futures);
		}
	}

	private void send(int priority, ByteBuffer encodedPacket, NetworkConnection.SendResult futures) {
		whenBandwidthExists(encodedPacket).thenAccept(buffer -> {
			try {
				sendBuffer(encodedPacket.duplicate());
				futures.sent().complete(null);

				if (priority != -1) {
					CompletableFuture.delayedExecutor(resendDelay.toNanos(), TimeUnit.NANOSECONDS).execute(() -> {
						if (!futures.received().isDone()) {
							resendQueue.add(new SentPacket(Instant.now(), priority, buffer, futures));
						}
					});
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}).exceptionally(e -> {
			futures.sent().completeExceptionally(e);
			return null;
		});

		futures.sent().thenRun(() -> resendQueue.add(new SentPacket(Instant.now(), priority, encodedPacket, futures)));
	}

	private final long maximumByteAccumulation = 1024 * 1024;
	private final long nanosPerByte = 1_000_000_000L / 1024 / 1024;

	/**
	 * The time at which the connection had zero bandwidth
	 */
	private Instant timeOfZeroBytes = Instant.now().minusNanos(maximumByteAccumulation * nanosPerByte);

	private CompletableFuture<ByteBuffer> whenBandwidthExists(ByteBuffer buffer) {
		int bytes = buffer.remaining();

		var now = Instant.now();
		var maximumTimeIntoThePast = now.minusNanos(maximumByteAccumulation * nanosPerByte);
		timeOfZeroBytes = timeOfZeroBytes.plusNanos(bytes * nanosPerByte);

		if (maximumTimeIntoThePast.isAfter(timeOfZeroBytes)) {
			timeOfZeroBytes = maximumTimeIntoThePast;
		}

		long nanosToWait = Duration.between(now, timeOfZeroBytes).toNanos();

		var future = new CompletableFuture<ByteBuffer>();
		CompletableFuture.delayedExecutor(nanosToWait, TimeUnit.NANOSECONDS).execute(() -> future.complete(buffer));
		return future;
	}
}
