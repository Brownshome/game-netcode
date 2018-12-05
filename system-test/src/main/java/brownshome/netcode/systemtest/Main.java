package brownshome.netcode.systemtest;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brownshome.netcode.*;
import brownshome.netcode.memory.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.*;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		List<Schema> protocol = List.of(new BaseSchema(), new TestSchema());

		MemoryConnectionManager clientConnectionManager = new MemoryConnectionManager(protocol);
		MemoryConnectionManager serverConnectionManager = new MemoryConnectionManager(protocol);

		ExecutorService executor = Executors.newFixedThreadPool(20);
		
		serverConnectionManager.registerExecutor("default", executor, 1000);
		clientConnectionManager.registerExecutor("default", executor, 1000);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager);

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
