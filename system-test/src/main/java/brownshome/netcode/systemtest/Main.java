package brownshome.netcode.systemtest;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brownshome.netcode.*;
import brownshome.netcode.memory.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.*;
import brownshome.netcode.udp.UDPConnectionManager;
import brownshome.netcode.udp.UDPSchema;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		List<Schema> protocol = List.of(new BaseSchema(), new UDPSchema(), new TestSchema());

		UDPConnectionManager clientConnectionManager = new UDPConnectionManager(protocol, 25565);
		UDPConnectionManager serverConnectionManager = new UDPConnectionManager(protocol, 25566);

		ExecutorService executor = Executors.newFixedThreadPool(20);
		
		serverConnectionManager.registerExecutor("default", executor, 24);
		clientConnectionManager.registerExecutor("default", executor, 24);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager.address());

		connection.connectSync();

		for(int i = 0; i < 100; i++) {
			connection.send(new LongProcessingPacket(500l));
		}

		//As this packet is reliable, this should cause the system to
		connection.sendSync(new CauseErrorPacket());

		clientConnectionManager.close();
		serverConnectionManager.close();

		executor.shutdown();
	}
}
