package brownshome.netcode.util;

import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import brownshome.netcode.NetworkConnection;
import brownshome.netcode.Packet;

/**
 * Dispatches packets for sending base on when they have already been published into an aggregate packet. This queue
 * also tracks reliable packet reception for the purposes of flushing.
 */
public final class PacketSendQueue extends PacketQueue {
	private static final int RELIABLE_ID = 1;

	private final NetworkConnection<?, ?> connection;
	private final PacketTypeMap types;

	public PacketSendQueue(NetworkConnection<?, ?> connection) {
		// barrier and reliable IDs are taken
		types = new PacketTypeMap(2);
		this.connection = connection;
	}

	private static class PacketSend implements ScheduledItem {
		final PacketTypeMap.PacketType type;
		final CompletableFuture<Void> queueForSending;
		final CompletableFuture<Void> sent;

		PacketSend(PacketTypeMap.PacketType type,
		           CompletableFuture<Void> queueForSending,
		           CompletableFuture<Void> sent) {
			this.type = type;
			this.queueForSending = queueForSending;
			this.sent = sent;
		}

		@Override
		public boolean processType(BitSet previous) {
			assert type.isComplete();

			if (!ScheduledItem.hasBarrier(previous) && !type.waitsFor().intersects(previous)) {
				queueForSending.complete(null);
			}

			if (!sent.isDone()) {
				previous.set(type.id());
			}

			return sent.isDone();
		}
	}

	private static class ReliablePacketSend extends PacketSend {
		final CompletableFuture<Void> received;

		ReliablePacketSend(PacketTypeMap.PacketType type,
		                   CompletableFuture<Void> queueForSending,
		                   CompletableFuture<Void> sent,
		                   CompletableFuture<Void> received) {
			super(type, queueForSending, sent);
			this.received = received;
		}

		@Override
		public boolean processType(BitSet previous) {
			if (!received.isDone()) {
				// Flushes and barriers will now wait for reliable packets to complete
				previous.set(RELIABLE_ID);
			}

			return super.processType(previous) && received.isDone();
		}
	}

	public CompletableFuture<Void> send(Packet packet) {
		var type = types.getType(packet);
		var queueForSending = new CompletableFuture<Void>();

		if (packet.reliable()) {
			// This is a bit weird, but we use the sent future to pass the received future
			var sent = queueForSending
					.thenCompose(unused -> {
						var futures = connection.queueForSending(packet, type.id(), type.waitsFor());
						return futures.sent().thenApply(u -> futures.received());
					});
			var received = sent.thenCompose(Function.identity());

			schedule(new ReliablePacketSend(type, queueForSending, sent.thenRun(() -> {}), received));
			return received;
		} else {
			var sent = queueForSending
				.thenCompose(unused -> connection.queueForSending(packet, type.id(), type.waitsFor()).sent());

			schedule(new PacketSend(type, queueForSending, sent));
			return sent;
		}
	}

	public NetworkConnection.SendResult sendImmediately(Packet packet) {
		var type = types.getType(packet);
		return connection.queueForSending(packet, type.id(), type.waitsFor());
	}
}
