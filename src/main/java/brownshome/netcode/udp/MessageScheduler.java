package brownshome.netcode.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
	private static final System.Logger LOGGER = System.getLogger(MessageScheduler.class.getModule().getName());

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

	/** Score cutoff is used to conserve bandwidth. */
	static final double SCORE_CUTTOFF = 1.0;

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

	/* ******** PACKET STORAGE ********* */

	/** This is a collection of scheduled packets that have been scheduled but not yet received */
	private final Collection<ScheduledPacket> packetsNotReceived;

	/** The set of messages that need to be sent. */
	private final Map<Integer, ConstructedDataPacket> sentPackets;

	/* ******** ACK VARIABLES ********** */

	/** The sequence number of the next packet to be sent */
	private int nextSequenceNumber = 0;

	private final AckSender ackSender = new AckSender();

	/** A future used to signal when all acks have been sent. */
	private CompletableFuture<Void> acksSentFuture = null;
	
	MessageScheduler(UDPConnection connection) {
		this.connection = connection;
		rttEstimate = Duration.ofMillis(200);
		bandwidthEstimate = 1e6;
		packetLoss = 0.0;
		sentPackets = new HashMap<>();
		packetsNotReceived = new ArrayList<>();

		connection.connectionManager().submissionThread().scheduleAtFixedRate(this::queuePackets, 0, 1, TimeUnit.MILLISECONDS);
	}

	/** This method is called every X time units to trigger the packet sending code. */
	private synchronized void queuePackets() {
		LOGGER.log(System.Logger.Level.TRACE, "Queueing packets to '" + connection.address() + "'");

		now = Instant.now();

		// TODO blocking mechanics

		// Calculate bandwidth
		bytesToSend += (lastSendAttempt.until(now, ChronoUnit.NANOS) / 1e9) * bandwidthEstimate;
		bytesToSend = Math.min(UDPConnectionManager.BUFFER_SIZE, bytesToSend);

		assembleAndSendMessages();

		lastSendAttempt = now;
	}

	/**
	 * A future that will be triggered when all acks that need to be sent have been sent.
	 */
	synchronized CompletableFuture<Void> closeMessageSchedulerFuture() {
		if (acksSentFuture != null) {
			return acksSentFuture;
		} else if (ackSender.hasUnsentAcks()) {
			return acksSentFuture = new CompletableFuture<>();
		} else {
			return acksSentFuture = CompletableFuture.completedFuture(null);
		}
	}

	/** This sends up to bytesToSend bytes of data. */
	private void assembleAndSendMessages() {
		// The options for sending packets are as follows...
		// Either resend an old message, or create a new message.

		if (packetsNotReceived.isEmpty()) {
			// Send an ack packet

			if (ackSender.hasUnsentAcks()) {
				sendConstructedDataPacket(new ConstructedDataPacket(nextSequenceNumber++, connection, 0));
			} else if (acksSentFuture != null) {
				acksSentFuture.complete(null);
			}

			return;
		}

		TreeSet<ScheduledPacket> sortedPackets = new TreeSet<>(packetsNotReceived);

		// While there is bandwidth left, produce data-packets and send them

		while(!sortedPackets.isEmpty()) {
			var mostImportantPacket = sortedPackets.first();

			if (mostImportantPacket.score() < SCORE_CUTTOFF) {
				break;
			}

			ConstructedDataPacket toSend;

			if (mostImportantPacket.containingPacket() != null && mostImportantPacket.containingPacket().dataBuffer.remaining() <= bytesToSend) {
				toSend = mostImportantPacket.containingPacket();
			} else {
				int length = (int) Math.min(MessageScheduler.MTU, bytesToSend);
				toSend = new ConstructedDataPacket(nextSequenceNumber, connection, length);

				for (var possibleChild : sortedPackets) {
					if(possibleChild.containingPacket() != null) {
						continue;
					}

					if (toSend.dataBuffer.remaining() >= possibleChild.size()) {
						toSend.addPacket(possibleChild);
					}
				}

				// Change to send mode.
				toSend.dataBuffer.flip();

				if (toSend.children().size() == 0) {
					// There are no packets that can be sent.
					break;
				}

				nextSequenceNumber++;
			}

			sortedPackets.removeAll(toSend.children());
			sendConstructedDataPacket(toSend);
		}
	}

	private void sendConstructedDataPacket(ConstructedDataPacket toSend) {
		LOGGER.log(System.Logger.Level.DEBUG, String.format("Sending '%s' to '%s'", toSend, connection.address()));

		bytesToSend -= toSend.dataBuffer.remaining();

		sentPackets.put(toSend.sequenceNumber, toSend);
		toSend.signalSend(now);

		var ack = ackSender.createAck();

		UDPDataPacket packet = new UDPDataPacket(
				UDPPackets.hashDataPacket(connection.remoteSalt(), ack.largestAck, ack.field, toSend.sequenceNumber, toSend.dataBuffer.duplicate()),
				ack.largestAck, ack.field, toSend.sequenceNumber, toSend.dataBuffer.duplicate());

		sendDirectlyToChannel(packet);
	}

	/** Dispatches a packet directly to the channel */
	private void sendDirectlyToChannel(Packet packet) {
		ByteBuffer aggregateBuffer = ByteBuffer.allocate(connection.calculateEncodedLength(packet));
		connection.encode(aggregateBuffer, packet);
		aggregateBuffer.flip();

		try {
			connection.connectionManager().channel().send(aggregateBuffer, connection.address());
		} catch(IOException e) {
			LOGGER.log(System.Logger.Level.ERROR, "Unable to send", e);
			// TODO close connection
			throw new RuntimeException(e);
		}
	}

	/** This is called when an ack is received for a particular group of messages. */
	synchronized void ackReceived(Ack ack) {
		LOGGER.log(System.Logger.Level.DEBUG, "Remote address '" + connection.address() + "' sent acks for '" + Arrays.toString(ack.ackedPackets) + "'");

		// TODO estimate packet loss
		// TODO estimate rtt

		for(int sequenceNumber : ack.ackedPackets) {
			ConstructedDataPacket ackedDataPacket = sentPackets.remove(sequenceNumber);

			if(ackedDataPacket == null) {
				continue;
			}

			ackedDataPacket.signalReceived();

			for(var child : ackedDataPacket.children()) {
				packetsNotReceived.remove(child);
			}
		}
	}

	synchronized void receiveSequenceNumber(int sequenceNumber) {
		LOGGER.log(System.Logger.Level.DEBUG, "Remote address '" + connection.address() + "' sent sequence number '" + sequenceNumber + "'");

		ackSender.receivedPacket(sequenceNumber);
	}

	private static final int RUNNING_AVG_LENGTH = 10;

	private void updateRttEstimate(Duration rtt) {
		rttEstimate = rttEstimate.multipliedBy(RUNNING_AVG_LENGTH - 1).plus(rtt).dividedBy(RUNNING_AVG_LENGTH);
	}

	/** Schedules a packet into the messaging queue. The packet order is defined by the order in which this method is called. */
	synchronized CompletableFuture<Void> schedulePacket(Packet packet) {
		LOGGER.log(System.Logger.Level.DEBUG, "Scheduled '" + packet + "' for sending to '" + connection.address() + "'");

		var future = new CompletableFuture<Void>();

		packetsNotReceived.add(new ScheduledPacket(packet, future, this));

		return future;
	}

	/** This is the chance that a packet sent at time X will eventually arrive, given that an ack has not been received. */
	double chanceOfPacketArriving(Instant timeSent) {
		Duration largerThanRTT = rttEstimate.multipliedBy(2);

		if(timeSent.plus(largerThanRTT).compareTo(now) < 0) {
			return 0.0; //We should have received an Ack by now
		} else {
			return 1.0 - packetLoss;
		}
	}

	/** Returns a time that can be considered 'now' this time is updated every send cycle. */
	Instant now() {
		return now;
	}
}
