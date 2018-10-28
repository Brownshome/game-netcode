package brownshome.netcode;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class handles the incoming connections and creates outgoing connections.
 * @author James Brown
 */
public interface ConnectionManager<ADDRESS, CONNECTION extends Connection<ADDRESS>> extends Closeable {
	/** 
	 * Gets a connection to an address.
	 * @return A connection object. This connection may not have been connected.
	 **/
	CONNECTION getOrCreateConnection(ADDRESS address);
	
	/** Registers an executor that incoming packets can be executed on. */
	void registerExecutor(String name, Executor executor);

	/** Returns a list of all schemas that should be used with this connection. */
	List<Schema> schemas();

	/**
	 * This method closes the ConnectionManager, any connections that the manager has open will be closed and any listener
	 * threads that the manager has open will cease to function.
	 */
	@Override
	void close() throws IOException;
}
