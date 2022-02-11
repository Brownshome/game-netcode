package brownshome.netcode.memory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import brownshome.netcode.*;

/**
 * This is a connection that connects two MemoryConnectionManagers
 **/
public class MemoryConnection extends Connection<MemoryConnectionManager, MemoryConnectionManager> {
	private final CompletableFuture<Void> readyToSend;
	private volatile boolean closed;

	protected MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		super(manager, other, Protocol.baseProtocol());

		readyToSend = new CompletableFuture<>();
		closed = false;

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
}
