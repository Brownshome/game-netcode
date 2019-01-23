package brownshome.netcode;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class handles the incoming connections and creates outgoing connections.
 * @author James Brown
 */
public interface ConnectionManager<ADDRESS, CONNECTION extends Connection<ADDRESS>> extends AutoCloseable {
	/** 
	 * Gets a connection to an address.
	 * @return A connection object. This connection may not have been connected.
	 **/
	CONNECTION getOrCreateConnection(ADDRESS address);
	
	/**
	 * Registers an executor that incoming packets can be executed on.
	 *
	 * @param concurrency The maximum number of calls that will be in flight at the same time.
	 **/
	void registerExecutor(String name, Executor executor, int concurrency);

	/** Returns a list of all schemas that should be used with this connection. */
	List<Schema> schemas();

	/**
	 * This method closes the ConnectionManager, any connections that the manager has open will be closed and any listener
	 * threads that the manager has open will cease to function. Any messages that are queued for sending in any of the connections will attempt to send.
	 *
	 * Interrupting this method will stop the waiting for message sending.
	 */
	@Override
	void close();

	/**
	 * Gets the address of this connection manager. This is the address that other clients should connect to if they want
	 * to connect to this manager.
	 */
	ADDRESS address();
}
