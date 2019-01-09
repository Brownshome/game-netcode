package brownshome.netcode.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import brownshome.netcode.ErrorPacket;
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
	private Instant lastSend = Instant.now();
	
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
	private int sequenceNumber = 0;

	private final AckSender ackSender = new AckSender();

	/** A struct that represents a sent group of messages. */
	private static final class SentPacket {
		final Collection<ScheduledPacket> packets;
		final Collection<CompletableFuture<Void>> futures;
		final int sequenceNumber;
		final Instant timeSent;

		SentPacket(Collection<ScheduledPacket> packets, Collection<CompletableFuture<Void>> futures,
		           int sequenceNumber, Instant timeSent) {
			this.packets = packets;
			this.futures = futures;
			this.sequenceNumber = sequenceNumber;
			this.timeSent = timeSent;
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
	private void queuePackets() {
		now = Instant.now();

		// TODO blocking mechanics

		// Sort the packet
		List<ScheduledPacket> sortedPackets = new ArrayList<>(packets);
		sortedPackets.sort(null);

		// Calculate bandwidth
		bytesToSend += (lastSend.until(now, ChronoUnit.NANOS) / 1e9) * bandwidthEstimate;
		bytesToSend = Math.min(UDPConnectionManager.BUFFER_SIZE, bytesToSend);

		// While there is bandwidth left, send packets
		// This index is one past the last packet to be sent in this burst
		int sendIndex = 0;
		while(sendIndex < sortedPackets.size() && bytesToSend > 0) {
			// Send packet
			ScheduledPacket nextSend = sortedPackets.get(sendIndex++);
			bytesToSend -= nextSend.remainingData();
		}

		// TODO fragmentation
		// TODO pack packets into packets more intelligently.
		// TODO limit copies of data

		List<ScheduledPacket> packetsToSend = new LinkedList<>(sortedPackets.subList(0, sendIndex));
		packetsToSend.sort(Comparator.comparingInt(ScheduledPacket::remainingData));

		while(!packetsToSend.isEmpty()) {
			ByteBuffer buffer = ByteBuffer.allocate(MTU);

			for(ListIterator<ScheduledPacket> it = packetsToSend.listIterator(); it.hasNext(); ) {
				ScheduledPacket next = it.next();

				if(buffer.remaining() < next.remainingData()) {
					continue;
				}

				if(!next.packet.reliable()) {
					next.future.complete(null);
				} else {
					throw new UnsupportedOperationException();
				}

				it.remove();

				next.write(buffer);
			}

			buffer.flip();

			int nextSequenceNumberByAcks = ackSender.mostRecentAck() + 1;
			if(nextSequenceNumberByAcks - sequenceNumber > 0) {
				sequenceNumber = nextSequenceNumberByAcks;
			}

			int acks = ackSender.createAckField(sequenceNumber);
			int hash = UDPPackets.hashDataPacket(connection.remoteSalt(), acks, sequenceNumber, buffer.duplicate());
			UDPDataPacket aggregatePacket = new UDPDataPacket(hash, acks, sequenceNumber, buffer);

			sequenceNumber++;
			ByteBuffer aggregateBuffer = ByteBuffer.allocate(connection.calculateEncodedLength(aggregatePacket));
			connection.encode(aggregateBuffer, aggregatePacket);
			aggregateBuffer.flip();

			try {
				connection.connectionManager().channel().send(aggregateBuffer, connection.address());
			} catch(IOException e) {
				LOGGER.log(Level.SEVERE, "Unable to send", e);
				// TODO close connection
				throw new RuntimeException(e);
			}
		}

		//TODO remove this line
		packets.clear();
	}

	/** This is called when an ack is received for a particular group of messages. */
	void ackReceived(Ack ack) {
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

			Duration rtt = Duration.between(packet.timeSent, Instant.now());

			updateRttEstimate(rtt);
		}
	}

	void receiveSequenceNumber(int sequenceNumber) {
		ackSender.receivedPacket(sequenceNumber);
	}

	private static int RUNNING_AVG_LENGTH = 10;

	private void updateRttEstimate(Duration rtt) {
		rttEstimate = rttEstimate.multipliedBy(RUNNING_AVG_LENGTH - 1).plus(rtt).dividedBy(RUNNING_AVG_LENGTH);
	}

	/** Schedules a packet into the messaging queue. The packet order is defined by the order in which this method is called. */
	CompletableFuture<Void> schedulePacket(Packet packet) {
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
