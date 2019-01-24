package brownshome.netcode.systemtest;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import brownshome.netcode.*;
import brownshome.netcode.memory.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.*;
import brownshome.netcode.udp.UDPConnectionManager;
import brownshome.netcode.udp.UDPSchema;

public class Main {
	private static void setupLogging() {
		try {
			LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
		} catch(IOException e) {
			throw new IllegalStateException("Unable to find logging configuration file", e);
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		setupLogging();

		Logger.getLogger("brownshome.netcode").fine("Fine logging enabled");

		List<Schema> protocol = List.of(new BaseSchema(), new UDPSchema(), new TestSchema());

		UDPConnectionManager clientConnectionManager = new UDPConnectionManager(protocol, 25565);
		UDPConnectionManager serverConnectionManager = new UDPConnectionManager(protocol, 25567);

		ExecutorService executor = Executors.newFixedThreadPool(20);
		
		serverConnectionManager.registerExecutor("default", executor, 24);
		clientConnectionManager.registerExecutor("default", executor, 24);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(new InetSocketAddress("localhost", 25566));

		connection.connectSync();

		clientConnectionManager.close();
		serverConnectionManager.close();

		executor.shutdown();
	}
}
