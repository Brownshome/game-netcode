package brownshome.netcode.util;

import java.util.BitSet;
import java.util.concurrent.*;

import brownshome.netcode.*;

/**
 * Handles the execution of packets, ensuring that no packet is executed out-of-order with respect to its ordering
 * guarantees.
 */
public final class PacketExecutor extends BitSetPacketQueue {
	private final Connection<?, ?> connection;

	private record PacketExecution(
			PacketTypeMap.PacketType type,
			CompletableFuture<Void> start,
			CompletableFuture<Void> finish
	) implements ScheduledItem<BitSet> {
		@Override
		public ProcessingResult<BitSet> processType(BitSet previous) {
			assert type.isComplete();

			if (!BitSetPacketQueue.hasBarrier(previous) && !type.waitsFor().intersects(previous)) {
				start.complete(null);
			}

			if (!finish.isDone()) {
				previous.set(type.id());
			}

			return new ProcessingResult<>(previous, finish.isDone());
		}
	}

	private final PacketTypeMap types;

	public PacketExecutor(Connection<?, ?> connection) {
		types = new PacketTypeMap(1);
		this.connection = connection;
	}

	/**
	 * Schedules a packet for execution
	 * @param packet the packet to execute
	 *
	 * @return a completion stage representing the result of the packet handling
	 */
	public CompletableFuture<Void> execute(Packet packet) {
		var type = types.getType(packet);
		var startFuture = new CompletableFuture<Void>();
		var finishFuture = startFuture.thenRunAsync(() -> {
			try {
				connection.protocol().handle(connection, packet);
			} catch (NetworkException ne) {
				connection.send(new ErrorPacket(ne.getMessage()));
			}
		}, connection.connectionManager().executorService(packet.getClass()));

		schedule(new PacketExecution(type, startFuture, finishFuture));

		sweepSchedule();
		finishFuture.whenComplete((unused, throwable) -> sweepSchedule());

		return finishFuture;
	}
}
