package brownshome.netcode.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import brownshome.netcode.Packet;

/** 
 * This is a helper class for the UDP subsystem that handles the dispatching of messages 
 * <br>
 * This class assembles packets of size MTU out of the messages currently waiting to be sent in the buffer. It has several goals:
 * 
 * > Keep the used bandwidth of the connection low enough so that latency does not increase.
 * > Ensure that reliable packets arrive.
 * > Do not send any reliably ordered packets out of order until the first one has been acked.
 * > Limit the latency as much as possible, prioritise the latency of higher priority packets over lower priority packets.
 * 
 * If these have been achieved then the following goals should be examined:
 * 
 * > Replicate the sending of all packets to ensure that the arrive.
 * 
 * To accomplish this the class will keep track of the approximate bandwidth of the connection and accumulate bits. A minimum delay between packets will be
 * introduced to ensure that bandwidth is not wasted on connection overheads.
 **/
final class MessageScheduler {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");

	/*

	Packet accumulate score depending on:

	> Their priority
	> Whether they have been sent
	> Whether they have been un-acked for more than the RTT * 1.5
	> How long the packet has been waiting

	The packets are then chosen so that the highest score per byte is sent first. Packets that cause the blocking of another packet get the maximum of their score and the packet it is blocking.

	Packets are dropped from the queue when:

	> They have been acked.
	> They are too old and they are not reliable.

	Each packet keeps track of:

	> Chance it was sent
	> Size
	> Age
	> Priority

	Score = (Priority + Priority * Age * A) * (1 - chance)

	Where S is a tuning parameter

	A weights priority more towards high priority packets regardless of age.

	 */

	/** This is a conservative estimate for the number of bytes of data that can fit in the body of a packet. Leaving ample space for headers. */
	static final int MTU = 1024;

	/** This number is used to delimit sequence numbers: SEQ = PACKET_SEQ * MAXIMUM_MESSAGES_PER_PACKET + MESSAGE_NO */
	static final int MAXIMUM_MESSAGES_PER_PACKET = 128;

	/** Tuning parameter, relating the the aging rate 1/500 = + priority every 500ms */
	private static final double A = 1.0 / 500.0;

	/** Estimated round trip time. */
	private Duration rttEstimate;
	
	/** Estimated bandwidth in B/s */
	private double bandwidthEstimate;

	private long bytesToSend = 0;
	private Instant lastSendAttempt = Instant.now();

	/** Estimated packet loss (1.0 - 0.0) */
	private double packetLoss;

	/** The connection object that messages are dispatched to. */
	private final UDPConnection connection;

	/** Now is stored centrally to ensure that comparators operate to specification. */
	private Instant now;

	/** The set of messages that need to be sent */
	private final Set<ScheduledPacket> packets;

	/* ******** ACK VARIABLES ********** */

	/** The sequence number of the next packet to be sent */
	private int nextSequenceNumber = 0;

	/** True if a reliable packet has been received since the last send. */
	private AtomicBoolean outstandingReliableAcks = new AtomicBoolean(false);

	private final AckSender ackSender = new AckSender();

	private CompletableFuture<Void> acksSentFuture = null;

	/** A struct that represents a sent group of messages. */
	private static final class SentPacket {
		final Collection<ScheduledPacket> packets;
		final Collection<CompletableFuture<Void>> futures;
		final ByteBuffer data;
		final int sequenceNumber;

		Instant lastTimeSent;
		int sendCount = 1;

		SentPacket(Collection<ScheduledPacket> packets, Collection<CompletableFuture<Void>> futures,
		           int sequenceNumber, Instant timeSent, ByteBuffer buffer) {
			this.packets = packets;
			this.futures = futures;
			this.sequenceNumber = sequenceNumber;
			lastTimeSent = timeSent;
			data = buffer;
		}

		void resend(Instant now) {
			sendCount++;
			lastTimeSent = now;


		}
	}

	/** The mapping of ack numbers to the packets that they match to. */
	private final LinkedHashMap<Integer, SentPacket> sequenceMapping;
	
	MessageScheduler(UDPConnection connection) {
		this.connection = connection;
		rttEstimate = Duration.ofMillis(200);
		bandwidthEstimate = 1e6;
		packetLoss = 0.0;
		packets = new HashSet<>();
		sequenceMapping = new LinkedHashMap<>();

		connection.connectionManager().submissionThread().scheduleAtFixedRate(this::queuePackets, 0, 1, TimeUnit.MILLISECONDS);
	}

	/** This method is called every X time units to trigger the packet sending code. */
	private synchronized void queuePackets() {
		now = Instant.now();

		// TODO blocking mechanics

		// Calculate bandwidth
		bytesToSend += (lastSendAttempt.until(now, ChronoUnit.NANOS) / 1e9) * bandwidthEstimate;
		bytesToSend = Math.min(UDPConnectionManager.BUFFER_SIZE, bytesToSend);

		boolean priorFlag = outstandingReliableAcks.getAndSet(false); //If the flag is set after this time, then we need to trigger the send next cycle.

		int sent = assembleAndSendMessages();
		bytesToSend -= sent;

		if(sent == 0) {
			if(priorFlag) {
				// Keep it false, as this clears the flag, if someone set it to true in the meantime pick it up on the next iteration

				LOGGER.fine("Sending ack-only packet to '" + connection.address() + "'");

				sendDirectlyToChannel(createUDPDataPacket(ByteBuffer.allocate(0)));
			}
		}

		if(acksSentFuture != null && !acksSentFuture.isDone()) {
			acksSentFuture.complete(null);
		}

		//TODO remove this line
		packets.clear();

		lastSendAttempt = now;
	}

	/**
	 * A future that will be triggered when all acks that need to be sent have been sent.
	 */
	synchronized CompletableFuture<Void> closeMessageSchedulerFuture() {
		if(acksSentFuture != null) {
			return acksSentFuture;
		} else if(outstandingReliableAcks.get()) {
			return acksSentFuture = new CompletableFuture<>();
		} else {
			return acksSentFuture = CompletableFuture.completedFuture(null);
		}
	}

	/** Ensures that an ack will be sent in a timely manor, regardless of if data needs to be sent. */
	synchronized void flagReliableAck() {
		outstandingReliableAcks.set(true);
	}

	/** This sends up to bytesToSend bytes of data, and returns the amount of data actually sent. */
	private int assembleAndSendMessages() {
		// The options for sending packets are as follows...
		// Either resend an old message, or create a new message.

		// For now, send any old message that has a send time less then now - RTT * 2
		// If no such message exists send a new packet.

		/*int sendIndex = 0;
		int bytesSent = 0;

		while(sendIndex < sortedPackets.size()) {
			// Send packet

			ScheduledPacket nextSend = sortedPackets.get(sendIndex);
			if(bytesToSend - bytesSent < nextSend.remainingData()) {
				break;
			}

			sendIndex++;
			bytesSent += nextSend.remainingData();
		}

		// TODO fragmentation
		// TODO pack packets into packets more intelligently.
		// TODO limit copies of data

		List<ScheduledPacket> packetsToSend = new LinkedList<>(sortedPackets.subList(0, sendIndex));
		packetsToSend.sort(Comparator.comparingInt(ScheduledPacket::remainingData));

		while(!packetsToSend.isEmpty()) {
			Collection<ScheduledPacket> packetsSent = new ArrayList<>();
			Collection<CompletableFuture<Void>> reliableFutures = new ArrayList<>();

			ByteBuffer buffer = ByteBuffer.allocate(MTU);

			for(ListIterator<ScheduledPacket> it = packetsToSend.listIterator(); it.hasNext(); ) {
				ScheduledPacket next = it.next();

				if(buffer.remaining() < next.remainingData()) {
					continue;
				}

				if(!next.packet.reliable()) {
					next.future.complete(null);
				} else {
					reliableFutures.add(next.future);
				}

				it.remove();

				packetsSent.add(next);
				next.write(buffer);
			}

			buffer.flip();

			UDPDataPacket aggregatePacket = createUDPDataPacket(buffer);
			sendDirectlyToChannel(aggregatePacket);

			//Make the entry in the sentPackets table
			sequenceMapping.put(aggregatePacket.sequenceNumberData, new SentPacket(packetsSent, reliableFutures, aggregatePacket.sequenceNumberData, buffer, now));
		}

		return bytesSent;*/

		return 0;
	}



	/** Dispatches a packet directly to the channel */
	private void sendDirectlyToChannel(Packet packet) {
		ByteBuffer aggregateBuffer = ByteBuffer.allocate(connection.calculateEncodedLength(packet));
		connection.encode(aggregateBuffer, packet);
		aggregateBuffer.flip();

		try {
			connection.connectionManager().channel().send(aggregateBuffer, connection.address());
		} catch(IOException e) {
			LOGGER.log(Level.SEVERE, "Unable to send", e);
			// TODO close connection
			throw new RuntimeException(e);
		}
	}

	/** Creates a UDPDataPacket with the given buffer. Use this for sending new packets. */
	private UDPDataPacket createUDPDataPacket(ByteBuffer buffer) {
		int nextSequenceNumberByAcks = ackSender.mostRecentAck() + 1;
		if(nextSequenceNumberByAcks - nextSequenceNumber > 0) {
			nextSequenceNumber = nextSequenceNumberByAcks;
		}

		return createUDPDataPacket(buffer, nextSequenceNumber++);
	}

	/** Creates a UDPDataPacket with the given buffer.use this for resending old packets */
	private UDPDataPacket createUDPDataPacket(ByteBuffer buffer, int sequenceNumber) {
		int acks = ackSender.createAckField(sequenceNumber);
		int hash = UDPPackets.hashDataPacket(connection.remoteSalt(), acks, sequenceNumber, buffer.duplicate());

		return new UDPDataPacket(hash, acks, sequenceNumber, buffer);
	}

	/** This is called when an ack is received for a particular group of messages. */
	synchronized void ackReceived(Ack ack) {
		LOGGER.fine("Remote address '" + connection.address() + "' sent acks for '" + Arrays.toString(ack.ackedPackets) + "'");

		// TODO estimate packet loss

		for(int sequenceNumber : ack.ackedPackets) {
			SentPacket packet = sequenceMapping.remove(sequenceNumber);

			if(packet == null) {
				continue;
			}

			packets.removeAll(packet.packets);

			for(CompletableFuture<Void> future : packet.futures) {
				future.complete(null);
			}

			Duration rtt = Duration.between(packet.lastTimeSent, Instant.now());

			updateRttEstimate(rtt);
		}
	}

	synchronized void receiveSequenceNumber(int sequenceNumber) {
		LOGGER.fine("Remote address '" + connection.address() + "' sent sequence number '" + sequenceNumber + "'");

		ackSender.receivedPacket(sequenceNumber);
	}

	private static int RUNNING_AVG_LENGTH = 10;

	private void updateRttEstimate(Duration rtt) {
		rttEstimate = rttEstimate.multipliedBy(RUNNING_AVG_LENGTH - 1).plus(rtt).dividedBy(RUNNING_AVG_LENGTH);
	}

	/** Schedules a packet into the messaging queue. The packet order is defined by the order in which this method is called. */
	synchronized CompletableFuture<Void> schedulePacket(Packet packet) {
		LOGGER.fine("Scheduled '" + packet + "' for sending to '" + connection.address() + "'");

		var future = new CompletableFuture<Void>();

		packets.add(new ScheduledPacket(packet, future));

		return future;
	}

	/** This is the chance that a packet sent at time X will eventually arrive, given that an ack has not been received. */
	private double chanceOfPacketArriving(Instant timeSent) {
		Duration largerThanRTT = rttEstimate.multipliedBy(2);

		if(timeSent.plus(largerThanRTT).compareTo(now) < 0) {
			return 0.0; //We should have received an Ack by now
		} else {
			return 1.0 - packetLoss;
		}
	}

	/** Represents a packet that has been dispatched to the UDP scheduler */
	private final class ScheduledPacket implements Comparable<ScheduledPacket> {
		final Packet packet;
		final Instant scheduled;
		final CompletableFuture<Void> future;

		double chance;
		ByteBuffer buffer;

		ScheduledPacket(Packet packet, CompletableFuture<Void> future) {
			this.packet = packet;
			this.scheduled = MessageScheduler.this.now;
			this.chance = 0.0;
			this.future = future;
		}

		/**
		 * This writes as much packet data as is left in the buffer. If the buffer is too small the write is only partial,
		 * and further calls to write will write more data.
		 **/
		void write(ByteBuffer out) {
			if(buffer == null) {
				// There is no buffer, this is the first write, check for fragmentation

				if(out.remaining() < connection.calculateEncodedLength(packet)) {
					assert false : "Fragments are not yet supported";

					buffer = ByteBuffer.allocate(connection.calculateEncodedLength(packet));
					connection.encode(buffer, packet);
					buffer.flip();

					// Now that we have created the buffer we can write
					write(out);
				} else {
					connection.encode(out, packet);
				}
			} else {
				assert false : "Fragments are not yet supported";

				// Buffer is not null, write out data from the buffer

				int transfer = Math.min(buffer.remaining(), out.remaining());

				int oldLimit = buffer.limit();
				buffer.limit(buffer.position() + transfer);
				out.put(buffer);
				buffer.limit(oldLimit);
			}
		}

		int remainingData() {
			if(buffer == null) {
				return connection.calculateEncodedLength(packet);
			} else {
				return buffer.remaining();
			}
		}

		double score() {
			double ageScale = scheduled.until(now, ChronoUnit.MILLIS) * A;

			return (packet.priority() + packet.priority() * ageScale) * (1.0 - chance);
		}

		@Override
		public int compareTo(ScheduledPacket o) {
			return Double.compare(score(), o.score());
		}
	}
}
