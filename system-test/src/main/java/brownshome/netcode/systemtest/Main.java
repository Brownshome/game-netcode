package brownshome.netcode.systemtest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brownshome.netcode.*;
import brownshome.netcode.systemtest.packets.TestSchema;
import brownshome.netcode.udp.UDPConnectionManager;
import brownshome.netcode.udp.UDPSchema;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		List<Schema> protocol = List.of(new BaseSchema(), new UDPSchema(), new TestSchema());

		UDPConnectionManager clientConnectionManager = new UDPConnectionManager(protocol);
		UDPConnectionManager serverConnectionManager = new UDPConnectionManager(protocol, 65535);

		ExecutorService executor = Executors.newFixedThreadPool(20);
		
		serverConnectionManager.registerExecutor("default", executor, 24);
		clientConnectionManager.registerExecutor("default", executor, 24);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(new InetSocketAddress("localhost", 65535));

		connection.connectSync();

		clientConnectionManager.close();
		serverConnectionManager.close();

		executor.shutdown();
	}
}
