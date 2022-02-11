package brownshome.netcode;

import java.util.*;
import java.util.concurrent.*;

/**
 * This class handles the incoming connections and creates outgoing connections.
 * @author James Brown
 */
public abstract class ConnectionManager<ADDRESS, CONNECTION extends Connection<ADDRESS, ?>> implements AutoCloseable {
	protected static final System.Logger LOGGER = System.getLogger(ConnectionManager.class.getModule().getName());

	private final Map<ADDRESS, CONNECTION> connections = new HashMap<>();
	private final List<Schema> schema;

	protected ConnectionManager(List<Schema> schema) {
		this.schema = schema;
	}

	/**
	 * Gets a connection to an address.
	 * @return A connection object. This connection may not have been connected.
	 **/
	public final CONNECTION getOrCreateConnection(ADDRESS address) {
		return connections.computeIfAbsent(address, this::createNewConnection);
	}

	/**
	 * Creates a new connection from the given address
	 * @param address the address
	 * @return a new connection object
	 */
	protected abstract CONNECTION createNewConnection(ADDRESS address);

	/**
	 * Returns the executor for a given packet type
	 * @param type the packet type
	 * @return an executor to run the packet's handling logic on
	 */
	public ExecutorService executorService(Class<? extends Packet> type) {
		return ForkJoinPool.commonPool();
	}

	/**
	 * A list of all schemas that should be used with this connection.
	 * @return the list
	 **/
	public final List<Schema> schemas() {
		return schema;
	}

	/**
	 * This method closes the ConnectionManager, any connections that the manager has open will be closed and any listener
	 * threads that the manager has open will cease to function. Any messages that are queued for sending in any of the connections will attempt to send.
	 *
	 * @throws InterruptedException if the close operation was interrupted
	 */
	@Override
	public void close() throws InterruptedException {
		for (var connection : connections.values()) {
			connection.closeConnection();
		}

		for (var connection : connections.values()) {
			try {
				connection.closeConnection().get();
			} catch (ExecutionException e) {
				logConnectionCloseError(connection, e.getCause());
				//Keep trying to exit.
			}
		}
	}

	public CompletableFuture<Void> closeAsync() {
		return CompletableFuture.allOf(connections.values().stream()
				.map(connection -> connection.closeConnection().exceptionally(throwable -> {
					logConnectionCloseError(connection, throwable);
					return null;
				})).toArray(CompletableFuture[]::new));
	}

	private void logConnectionCloseError(CONNECTION connection, Throwable e) {
		LOGGER.log(System.Logger.Level.ERROR,
				String.format("Connection '%s' failed to terminate cleanly", connection.address()),
				e);
	}

	/**
	 * Gets the address of this connection manager. This is the address that other clients should connect to if they want
	 * to connect to this manager.
	 *
	 * @return the address
	 */
	public abstract ADDRESS address();
}
