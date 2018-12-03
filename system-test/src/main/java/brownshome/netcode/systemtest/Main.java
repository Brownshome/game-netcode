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

		Executor executor = Executors.newFixedThreadPool(20);
		
		serverConnectionManager.registerExecutor("default", executor, 1000);
		clientConnectionManager.registerExecutor("default", executor, 1000);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager);

		connection.connectSync();

		while(true) {
			connection.send(new LongProcessingPacket(5000l));
			Thread.sleep(1000);
		}
	}
}
