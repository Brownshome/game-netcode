package brownshome.netcode.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import brownshome.netcode.ConnectionManager;
import brownshome.netcode.Schema;

/**
 * This implements a connection that forms a direct message passing service between connections in the same process.
 *
 * The connections can safely be on differing threads. Note that the packets are never de-serialized, and no packet schema
 * negotiation takes place with this connection.
 *
 * Connect always returns instantly.
 */
public class MemoryConnectionManager implements ConnectionManager<MemoryConnectionManager, MemoryConnection> {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");

	private final List<Schema> schema;
	
	private final Map<String, Executor> executors = new HashMap<>();
	private final Map<MemoryConnectionManager, MemoryConnection> connections = new HashMap<>();
	
	public MemoryConnectionManager(List<Schema> schema) {
		this.schema = schema;
	}
	
	@Override
	public MemoryConnection getOrCreateConnection(MemoryConnectionManager other) {
		return connections.computeIfAbsent(other, o -> new MemoryConnection(this, other));
	}

	@Override
	public void registerExecutor(String name, Executor executor) {
		executors.put(name, executor);
	}

	public void executeOn(Runnable runner, String thread) {
		executors.get(thread).execute(runner);
	}

	@Override
	public List<Schema> schemas() {
		return schema;
	}

	@Override
	public void close() {
		for(var connection : connections.values()) {
			connection.closeConnection();
		}

		for(var connection : connections.values()) {
			try {
				connection.closeConnection().get();
			} catch(InterruptedException e) {
				//Exit from the close operation.
				return;
			} catch(ExecutionException e) {
				LOGGER.log(Level.WARNING,
						String.format("Connection '%s' failed to terminate cleanly", connection.address()),
						e.getCause());
				//Keep trying to exit.
			}
		}
	}
}
