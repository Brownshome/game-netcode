package brownshome.netcode.systemtest;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import brownshome.netcode.*;
import brownshome.netcode.memory.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.*;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		List<Schema> protocol = List.of(new BaseSchema(), new TestSchema());

		MemoryConnectionManager clientConnectionManager = new MemoryConnectionManager(protocol);
		MemoryConnectionManager serverConnectionManager = new MemoryConnectionManager(protocol);

		Executor executor = Executors.newFixedThreadPool(5);
		
		serverConnectionManager.registerExecutor("default", executor);
		clientConnectionManager.registerExecutor("default", executor);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager);

		connection.connectSync();

		while(true) {
			connection.send(new LongProcessingPacket(50l));
			Thread.sleep(5l);
		}
	}
}
