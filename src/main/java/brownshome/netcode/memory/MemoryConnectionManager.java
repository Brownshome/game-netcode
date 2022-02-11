package brownshome.netcode.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import brownshome.netcode.*;

/**
 * This implements a connection that forms a direct message passing service between connections in the same process.
 *
 * The connections can safely be on differing threads. Note that the packets are never de-serialized, and no packet schema
 * negotiation takes place with this connection.
 *
 * Connect always returns instantly.
 */
public class MemoryConnectionManager extends ConnectionManager<MemoryConnectionManager, MemoryConnection> {
	public MemoryConnectionManager(List<Schema> schema) {
		super(schema);
	}

	@Override
	protected final MemoryConnection createNewConnection(MemoryConnectionManager memoryConnectionManager) {
		return new MemoryConnection(this, memoryConnectionManager);
	}

	@Override
	public MemoryConnectionManager address() {
		return this;
	}
}
