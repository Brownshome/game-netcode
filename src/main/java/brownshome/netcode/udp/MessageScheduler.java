package brownshome.netcode.udp;

import java.time.Duration;
import java.util.PriorityQueue;
import java.util.function.Consumer;

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
	/** This is a conservative estimate for the number of bytes of data that can fit in the body of a packet. Leaving ample space for headers. */
	private static final int MTU = 1024;
	
	/** Estimated round trip time. */
	private Duration rttEstimate = Duration.ofMillis(200);
	
	/** Estimated bandwidth in MB/s */
	private double bandwidthEstimate = 1.0;
	
	/** Estimated packet loss (1.0 - 0.0) */
	private double packetLoss = 0.0;
	
	private final UDPConnection connection;

	
	PriorityQueue<Packet> sendQueue
	
	MessageScheduler(UDPConnection connection) {
		this.connection = connection;
	}
	
	/** This is called when an ack is received for a particular group of messages. */
	void ackReceived(UDPAck ack) {
		// TODO notify reliable packets, estimate RTT, estimate packet loss
	}
	
	/** Schedules a packet into the messaging queue. The packet order is defined by the order in which this method is called. */
	void schedulePacket(Packet packet) {
		
	}
}
