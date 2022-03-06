package brownshome.netcode.udp;

import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import brownshome.netcode.*;
import brownshome.netcode.util.*;

final class UdpPacketExecutor extends PacketQueue<UdpPacketExecutor.ExecutionInformation> {
	class ExecutionInformation {
		final int sequenceNumber;
		final int receivedSequenceNumbers;
		final BitSet awaitingExecution;

		private ExecutionInformation() {
			this(firstNumberNotReceived.get() - 1, ~0, new BitSet());
		}

		private ExecutionInformation(int sequenceNumber, int receivedSequenceNumbers, BitSet awaitingExecution) {
			this.sequenceNumber = sequenceNumber;
			this.receivedSequenceNumbers = receivedSequenceNumbers;
			this.awaitingExecution = awaitingExecution;
		}

		private ExecutionInformation shiftUnreceived(int newSequenceNumber) {
			if (firstNumberNotReceived.compareAndSet(newSequenceNumber, newSequenceNumber + 1)) {
				// We have not found a sequence number not received yet
				return new ExecutionInformation(newSequenceNumber, ~0, awaitingExecution);
			} else {
				int newReceived = (receivedSequenceNumbers << newSequenceNumber - sequenceNumber) | 1;
				return new ExecutionInformation(newSequenceNumber, newReceived, awaitingExecution);
			}
		}
	}

	private record PacketExecution(
			int sequenceNumber,
			int waitingSequenceNumbers,
			PacketTypeMap.PacketType type,
			CompletableFuture<Void> start,
			CompletableFuture<Void> finish) implements ScheduledItem<ExecutionInformation> {
		@Override
		public ProcessingResult<ExecutionInformation> processType(ExecutionInformation previous) {
			assert type.isComplete();

			var next = previous.shiftUnreceived(sequenceNumber);

			if ((next.receivedSequenceNumbers & waitingSequenceNumbers) == 0
					&& !type.waitsFor().intersects(previous.awaitingExecution)) {
				start.complete(null);
			}

			if (!finish.isDone()) {
				next.awaitingExecution.set(type.id());
				return ProcessingResult.keep(next);
			} else {
				return ProcessingResult.remove(next);
			}
		}
	}

	private final PacketTypeMap types;
	private final Connection<?, ?> connection;
	private final AtomicInteger firstNumberNotReceived = new AtomicInteger(0);

	UdpPacketExecutor(Connection<?, ?> connection) {
		this.types = new PacketTypeMap(0);
		this.connection = connection;
	}

	@Override
	protected ExecutionInformation startingCommunication() {
		return new ExecutionInformation();
	}

	private void schedule(PacketExecution item) {
		var older = startingCommunication();

		for (var it = scheduled().listIterator(); it.hasNext(); ) {
			var next = (PacketExecution) it.next();

			if (item != null && item.sequenceNumber <= next.sequenceNumber) {
				it.previous();
				it.add(item);
				it.next();
				item = null;
			}

			var result = it.next().processType(older);

			if (result.shouldRemove()) {
				it.remove();
			}

			older = result.communication();
		}
	}

	CompletableFuture<Void> execute(int sequenceNumber, int waitingSequenceNumbers, Packet packet) {
		var type = types.getType(packet);
		var startFuture = new CompletableFuture<Void>();
		var finishFuture = startFuture.thenRunAsync(() -> {
			try {
				connection.protocol().handle(connection, packet);
			} catch (NetworkException ne) {
				connection.send(new ErrorPacket(ne.getMessage()));
			}
		}, connection.connectionManager().executorService(packet.getClass()));

		schedule(new PacketExecution(sequenceNumber, waitingSequenceNumbers, type, startFuture, finishFuture));
		finishFuture.whenComplete((unused, throwable) -> sweepSchedule());

		return finishFuture;
	}
}
