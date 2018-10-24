package brownshome.netcode.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import brownshome.netcode.ConnectionManager;
import brownshome.netcode.Schema;

/** The address of a memory connection is the pipe that it sends data into. */
public class MemoryConnectionManager implements ConnectionManager<MemoryConnectionManager, MemoryConnection> {
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
}
