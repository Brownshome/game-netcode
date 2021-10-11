package brownshome.netcode.memory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import brownshome.netcode.*;
import brownshome.netcode.util.ConnectionFlusher;
import brownshome.netcode.ordering.OrderingManager;
import brownshome.netcode.ordering.SequencedPacket;

/**
 * This is a connection that connects two MemoryConnectionManagers. This connection will never block the send method. But
 * it will drop packets if the other end of the connection is overloaded.
 *
 * Packets will be buffered up to a small number of packets. If a packet cannot be sent, non-reliable packets will be dropped
 * in preference to reliable packets.
 *
 * Packets will also be dropped based on how many packets they are holding up and / or their priority.
 **/
public class MemoryConnection implements Connection<MemoryConnectionManager> {

	private final MemoryConnectionManager other;
	private final MemoryConnectionManager manager;

	private Protocol protocol;
	private final ConnectionFlusher flusher;

	/** This queue is used to store packets before the connection is connected, and they can be executed. It is null when
	 * the connection is connected. */
	private List<Packet> preConnectQueue = new ArrayList<>();
	private final OrderingManager orderingManager;
	private int sequenceNumber = 0;

	/** This is a map from the packet to the future that represents it. */
	private final Map<Packet, CompletableFuture<Void>> futures = new HashMap<>();
	private CompletableFuture<Void> closingFuture = null;

	/** This mapping stores the list
	 * This is a thread-safe data structure */
	protected MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		this.other = other;
		this.manager = manager;

		orderingManager = new OrderingManager(this::execute, this::drop);

		this.protocol = Protocol.baseProtocol();
		this.flusher = new ConnectionFlusher();
	}

	private synchronized void execute(Packet packet) {
		other.executeOn(() -> {
			try {
				protocol().handle(this, packet);
			} catch (NetworkException ne) {
				var connection = other.getOrCreateConnection(manager);
				//If the connection has already connected this does nothing
				connection.connect();
				connection.send(new ErrorPacket(ne.getMessage()));
			}

			orderingManager.notifyExecutionFinished(packet);

			CompletableFuture<Void> future = futures.get(packet);
			if (future != null) {
				future.complete(null);
			}
		}, packet.handledBy());
	}

	private synchronized void drop(Packet packet) {
		assert false; //Packets should never be dropped
	}

	@Override
	public synchronized CompletableFuture<Void> send(Packet packet) {
		CompletableFuture<Void> result = new CompletableFuture<>();

		if (preConnectQueue != null) {
			futures.put(packet, result);
			preConnectQueue.add(packet);
		} else {
			if (packet.reliable()) {
				futures.put(packet, result);
			} else {
				result.complete(null);
			}

			sendImpl(packet);
		}

		return result;
	}

	private void sendImpl(Packet packet) {
		//TODO drop low priority and non-reliable packets before crashing on overload

		SequencedPacket sequencedPacket = new SequencedPacket(packet, sequenceNumber++);
		orderingManager.deliverPacket(sequencedPacket);
		orderingManager.trimPacketNumbers(sequenceNumber);
	}

	@Override
	public synchronized CompletableFuture<Void> flush() {
		return flusher.flush();
	}

	@Override
	public MemoryConnectionManager address() {
		return other;
	}

	@Override
	public synchronized CompletableFuture<Void> connect(List<Schema> schemas) {
		if (closingFuture != null) {
			return CompletableFuture.failedFuture(new NetworkException("This connection is closed", this));
		}

		if (preConnectQueue != null) {
			//The order of these operands does not matter.
			Protocol.ProtocolNegotiation negotiationResult = Protocol.negotiateProtocol(other.schemas(), schemas);
			protocol = negotiationResult.protocol();

			var savedQueue = preConnectQueue;
			preConnectQueue = null;

			for (Packet packet : savedQueue) {
				sendImpl(packet);
			}
		}

		return CompletableFuture.completedFuture(null);
	}

	@Override
	public synchronized CompletableFuture<Void> closeConnection() {
		if (closingFuture == null) {
			//TODO tell the other connection to shutdown

			closingFuture = flush().thenRun(() -> {
				//Remove this connection from the queue
				//TODO removeConnection();
			});
		}

		return closingFuture;
	}

	@Override
	public MemoryConnectionManager connectionManager() {
		return manager;
	}

	@Override
	public Protocol protocol() {
		return protocol;
	}
}
