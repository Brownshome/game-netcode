package brownshome.netcode;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

/**
 * This class handles the incoming connections and creates outgoing connections.
 * @author James Brown
 */
public interface ConnectionManager {
	Connection getConnection(InetSocketAddress address);
	
	/** Registers an executor that incoming packets can be executed on. */
	void registerExecutor(String name, Executor executor);
}
