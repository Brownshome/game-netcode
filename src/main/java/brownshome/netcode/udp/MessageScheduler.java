package brownshome.netcode.udp;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.PriorityQueue;

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
	public static final int MTU = 1024;

	/** This number is used to delimit sequence numbers: SEQ = PACKET_SEQ * MAXIMUM_MESSAGES_PER_PACKET + MESSAGE_NO */
	public static final int MAXIMUM_MESSAGES_PER_PACKET = 128;

	/** Tuning parameter, relating the the aging rate 1/500 = + priority every 500ms */
	private static final double A = 1.0 / 500.0;
	
	/** Estimated round trip time. */
	private Duration rttEstimate = Duration.ofMillis(200);
	
	/** Estimated bandwidth in MB/s */
	private double bandwidthEstimate = 1.0;
	
	/** Estimated packet loss (1.0 - 0.0) */
	private double packetLoss = 0.0;

	/** The connection object that messages are dispatched to. */
	private final UDPConnection connection;

	/** Now is stored centrally to ensure that comparators operate to specification. */
	private Instant now;

	/** The queue of messages that need to be sent */
	PriorityQueue<Packet> sendQueue;
	
	MessageScheduler(UDPConnection connection) {
		this.connection = connection;
	}
	
	/** This is called when an ack is received for a particular group of messages. */
	void ackReceived(Ack ack) {
		// TODO notify reliable packets, estimate RTT, estimate packet loss
	}
	
	/** Schedules a packet into the messaging queue. The packet order is defined by the order in which this method is called. */
	void schedulePacket(Packet packet) {
		
	}

	/** This is the change that a packet sent at time X will eventually arrive, given that an ack has not been received. */
	private double chanceOfPacketArriving(Instant timeSent) {
		Duration largerThanRTT = rttEstimate.multipliedBy(2);

		if(timeSent.plus(largerThanRTT).compareTo(now) < 0) {
			return 0.0; //We should have received an Ack by now
		} else {
			return 1.0 - packetLoss;
		}
	}

	/** Represents a packet that has been dispatched to the UDP scheduler */
	private final class ScheduledPacket {
		final Packet packet;
		final Instant scheduled;

		double chance;

		ScheduledPacket(Packet packet) {
			this.packet = packet;
			this.scheduled = MessageScheduler.this.now;
			this.chance = 0.0;
		}



		double score() {
			double ageScale = scheduled.until(now, ChronoUnit.MILLIS) * A;

			return (packet.priority() + packet.priority() * ageScale) * (1.0 - chance);
		}
	}
}
