package brownshome.netcode.memory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import brownshome.netcode.*;
import brownshome.netcode.util.PacketExecutor;

/**
 * This is a connection that connects two MemoryConnectionManagers
 **/
public class MemoryConnection extends Connection<MemoryConnectionManager, MemoryConnectionManager> {
	private final CompletableFuture<Void> readyToSend;
	private final PacketExecutor packetExecutor;
	private volatile boolean closed;

	protected MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		super(manager, other, Protocol.baseProtocol());

		readyToSend = new CompletableFuture<>();
		closed = false;
		packetExecutor = new PacketExecutor(this);

		executionWait(readyToSend);
	}

	protected final MemoryConnection otherConnection() {
		return address().getOrCreateConnection(connectionManager());
	}

	@Override
	public CompletableFuture<Void> send(Packet packet) {
		if (closed) {
			return CompletableFuture.failedFuture(new NetworkException("This connection is closed", this));
		}

		return otherConnection().execute(packet);
	}

	@Override
	public CompletableFuture<Void> flush() {
		return otherConnection().executionFlush();
	}

	private final Lock connectLock = new ReentrantLock();

	@Override
	public CompletableFuture<Void> connect(List<Schema> schemas) {
		if (closed) {
			return CompletableFuture.failedFuture(new NetworkException("This connection is closed", this));
		}

		var negotiationResult = Protocol.negotiateProtocol(schemas, address().schemas());
		if (!negotiationResult.succeeded()) {
			return CompletableFuture.failedFuture(new FailedNegotiationException("Missing schema: [%s]".formatted(
					negotiationResult.missingSchema().stream().map(Objects::toString).collect(Collectors.joining(", ")))));
		}

		// Attempt to lock both our connect lock and the other connections connect lock
		while (true) {
			try {
				connectLock.lock();

				if (!otherConnection().connectLock.tryLock()) {
					// The other connection must be trying to connect
					continue;
				}

				try {
					// We have locked both, perform the connection
					if (!readyToSend.isDone()) {
						// First connection
						protocol(negotiationResult.protocol());
						otherConnection().protocol(negotiationResult.protocol());
						readyToSend.complete(null);
						otherConnection().readyToSend.complete(null);

						return CompletableFuture.completedFuture(null);
					} else {
						// We need to barrier the connection process
						// Each connection only uses its own protocol, so they can be updated individually
						return CompletableFuture.allOf(
								executionBarrier(start -> start.thenRun(() -> protocol(negotiationResult.protocol()))),
								otherConnection().executionBarrier(start -> start.thenRun(() -> otherConnection().protocol(negotiationResult.protocol()))));
					}
				} finally { otherConnection().connectLock.unlock(); }
			} finally {	connectLock.unlock(); }
		}
	}

	@Override
	public CompletableFuture<Void> closeConnection() {
		closed = true;
		otherConnection().closed = true;

		// Flushes don't mind being stacked up, so this should work fine!
		return CompletableFuture.allOf(executionFlush(), otherConnection().executionFlush());
	}

	/**
	 * Executes an incoming packet on the correct handler. This method also ensures that the packet is not executed before
	 * any packets that should have a happens-before relationship with it for this connection.
	 * @param packet the packet to execute
	 * @return a future representing the result of executing the packet
	 */
	private CompletableFuture<Void> execute(Packet packet) {
		return packetExecutor.execute(packet);
	}

	/**
	 * Flushes all executing packets
	 * @return a future that completes when all packets have flushed
	 */
	private CompletableFuture<Void> executionFlush() {
		return packetExecutor.flush();
	}

	/**
	 * Causes all executed packets and flushes to wait for a given future
	 * @param wait the future to wait for
	 */
	private void executionWait(CompletableFuture<Void> wait) {
		packetExecutor.wait(wait);
	}

	/**
	 * Inserts an execution barrier
	 * @param barrier a function that takes a future representing the start of the barrier and returns the end of the barrier
	 * @return the future representing the end of the barrier
	 */
	private CompletableFuture<Void> executionBarrier(UnaryOperator<CompletableFuture<Void>> barrier) {
		return packetExecutor.barrier(barrier);
	}
}
