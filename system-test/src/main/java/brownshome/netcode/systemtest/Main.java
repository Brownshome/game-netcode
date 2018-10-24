package brownshome.netcode.systemtest;

import java.util.List;

import brownshome.netcode.Connection;
import brownshome.netcode.Schema;
import brownshome.netcode.memory.MemoryConnectionManager;
import brownshome.netcode.packets.BaseSchema;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		List<Schema> protocol = List.of(new BaseSchema());
		MemoryConnectionManager serverConnectionManager = new MemoryConnectionManager(protocol);
		MemoryConnectionManager clientConnectionManager = new MemoryConnectionManager(protocol);
		
		serverConnectionManager.registerExecutor("default", Runnable::run);
		clientConnectionManager.registerExecutor("default", Runnable::run);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager);
		
		connection.connect();
	}
}
