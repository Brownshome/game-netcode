package brownshome.netcode.udp;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** This represents a UDPDataPacket that has not yet had ack and hash data added to it. */
final class ConstructedDataPacket {
	/** The list of child packets */
	private final Collection<ScheduledPacket> childPackets;

	/** The encoded sub-packet data */
	final ByteBuffer dataBuffer;

	/** The sequence number of this packet */
	final int sequenceNumber;

	/** THe connection that this packet will be sent on. */
	private final UDPConnection connection;

	/**
	 * This is defined as the chance that the packet will be received at some point in the future given no further sends.
	 **/
	private double chance = 0.0;

	/**
	 * All of the times that this packet has been sent.
	 */
	private final List<Instant> sent;
	private Instant mostRecentSend;

	ConstructedDataPacket(int sequenceNumber, UDPConnection connection, int length) {
		this.childPackets = new ArrayList<>();
		this.connection = connection;
		this.dataBuffer = ByteBuffer.allocate(length);
		this.sequenceNumber = sequenceNumber;
		this.sent = new ArrayList<>();
	}

	void addPacket(ScheduledPacket packet) {
		packet.write(dataBuffer, connection);
		packet.setContainingPacket(this);
		childPackets.add(packet);
	}

	void signalSend(Instant now) {
		sent.add(now);
		mostRecentSend = now;
	}

	void signalReceived() {
		for(var child : childPackets) {
			child.signalReceived();
		}
	}

	Collection<ScheduledPacket> children() {
		return childPackets;
	}
}
