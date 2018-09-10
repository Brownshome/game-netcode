package brownshome.netcode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import brownshome.netcode.memory.MemoryConnection;

/** The address of a memory connection is the pipe that it sends data into. */
public class MemoryConnectionManager implements ConnectionManager<MemoryConnectionManager, MemoryConnection> {
	private final GlobalNetworkProtocol protocol;
	
	private final Map<String, Executor> executors = new HashMap<>();
	private final Map<MemoryConnectionManager, MemoryConnection> connections = new HashMap<>();
	
	public MemoryConnectionManager(GlobalNetworkProtocol protocol) {
		this.protocol = protocol;
	}
	
	@Override
	public MemoryConnection getConnection(MemoryConnectionManager other) {
		return connections.computeIfAbsent(other, o -> new MemoryConnection(this, other));
	}

	@Override
	public GlobalNetworkProtocol getGlobalProtocol() {
		return protocol;
	}

	@Override
	public void registerExecutor(String name, Executor executor) {
		executors.put(name, executor);
	}

	public void executeOn(Runnable runner, String thread) {
		executors.get(thread).execute(runner);
	}
}
