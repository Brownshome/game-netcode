package brownshome.netcode;

import java.util.concurrent.Executor;

/**
 * This class handles the incoming connections and creates outgoing connections.
 * @author James Brown
 */
public interface ConnectionManager<ADDRESS, CONNECTION extends Connection<ADDRESS>> {
	/** 
	 * Gets a connection to an address.
	 * @return A connection object. This connection may not have been connected.
	 **/
	CONNECTION getConnection(ADDRESS address);
	
	/** Registers an executor that incoming packets can be executed on. */
	void registerExecutor(String name, Executor executor);

	GlobalNetworkProtocol getGlobalProtocol();
}
