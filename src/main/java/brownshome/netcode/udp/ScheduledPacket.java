package brownshome.netcode.udp;

import brownshome.netcode.Packet;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

/** Represents a packet that has been dispatched to the UDP scheduler */
final class ScheduledPacket implements Comparable<ScheduledPacket> {
	/** Tuning parameter, relating the the aging rate 1/500 = + priority every 500ms */
	private static final double A = 1.0 / 500.0;

	private final MessageScheduler scheduler;

	private final CompletableFuture<Void> future;
	private final Packet packet;

	private final Instant scheduled;

	/** This buffer is filled with the raw packet data, and is used to write the packet into fragments. */
	private ByteBuffer buffer;

	private ConstructedDataPacket enclosingPacket;

	ScheduledPacket(Packet packet, CompletableFuture<Void> future, MessageScheduler scheduler) {
		this.packet = packet;
		this.scheduled = Instant.now();
		this.future = future;
		this.scheduler = scheduler;
	}

	void setContainingPacket(ConstructedDataPacket enclosingPacket) {
		this.enclosingPacket = enclosingPacket;
	}

	/**
	 * This writes as much packet data as is left in the buffer. If the buffer is too small the write is only partial,
	 * and further calls to write will write more data.
	 *
	 * @param out the buffer to write the data into
	 * @param connection the connection object that is used to encode the packet
	 *
	 **/
	void write(ByteBuffer out, UDPConnection connection) {
		if(buffer == null) {
			// There is no buffer, this is the first write, check for fragmentation

			int encodedLength = connection.calculateEncodedLength(packet);
			if(out.remaining() < encodedLength) {
				assert false : "Fragmentation is not supported";

				// Write to 'buffer' the full data-size of the packet
				// Then start to write fragments

				buffer = ByteBuffer.allocate(encodedLength);
				connection.encode(buffer, packet);
				buffer.flip();

				// Now that we have created the buffer we can write
				write(out, connection);
			} else {
				connection.encode(out, packet);
			}
		} else {
			assert false : "Fragmentation is not supported";

			// We are writing a fragment

			// Buffer is not null, write out data from the buffer

			int transfer = Math.min(buffer.remaining(), out.remaining());

			int oldLimit = buffer.limit();
			buffer.limit(buffer.position() + transfer);
			out.put(buffer);
			buffer.limit(oldLimit);

			buffer.remaining();
		}
	}

	double score() {
		double ageScale = scheduled.until(scheduler.now(), ChronoUnit.MILLIS) * A;

		return (packet.priority() + packet.priority() * ageScale) * (1.0 - computeChance());
	}

	private double computeChance() {
		if(enclosingPacket == null) {
			return 0.0;
		}

		// TODO

		return 0.0; //enclosingPacket.chanceOfTransit();
	}

	@Override
	public int compareTo(ScheduledPacket o) {
		return Double.compare(score(), o.score());
	}

	/** Updates tracking and other metrics to indicate that this packet was sent */
	void signalReceived() {
		if(future != null) {
			future.complete(null);
		}
	}

	/** Returns the size of the underlying packet */
	int size() {
		return packet.size();
	}

	ConstructedDataPacket containingPacket() {
		return enclosingPacket;
	}
}
