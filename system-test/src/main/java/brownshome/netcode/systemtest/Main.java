package brownshome.netcode.systemtest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import brownshome.netcode.Connection;
import brownshome.netcode.GlobalNetworkProtocol;
import brownshome.netcode.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.TestNetworkSchema;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		ExecutorService server = Executors.newSingleThreadExecutor();
		ExecutorService client = Executors.newSingleThreadExecutor();
		
		GlobalNetworkProtocol protocol = new GlobalNetworkProtocol(List.of(TestNetworkSchema.singleton()));
		MemoryConnectionManager serverConnectionManager = new MemoryConnectionManager(protocol);
		MemoryConnectionManager clientConnectionManager = new MemoryConnectionManager(protocol);
		
		serverConnectionManager.registerExecutor("DefaultHandler", server);
		clientConnectionManager.registerExecutor("DefaultHandler", client);
		
		Connection<?> connection = clientConnectionManager.getConnection(serverConnectionManager);
		
		connection.connect();
		
		server.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		client.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}
}
