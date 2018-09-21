package brownshome.netcode.systemtest;

import java.util.List;

import brownshome.netcode.Connection;
import brownshome.netcode.GlobalNetworkProtocol;
import brownshome.netcode.MemoryConnectionManager;
import brownshome.netcode.systemtest.packets.TestNetworkSchema;
import brownshome.netcode.systemtest.packets.TestPacket;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		GlobalNetworkProtocol protocol = new GlobalNetworkProtocol(List.of(TestNetworkSchema.singleton()));
		MemoryConnectionManager serverConnectionManager = new MemoryConnectionManager(protocol);
		MemoryConnectionManager clientConnectionManager = new MemoryConnectionManager(protocol);
		
		serverConnectionManager.registerExecutor("DefaultHandler", Runnable::run);
		clientConnectionManager.registerExecutor("DefaultHandler", Runnable::run);
		
		Connection<?> connection = clientConnectionManager.getConnection(serverConnectionManager);
		
		connection.connect();
		connection.send(new TestPacket("Message"));
	}
}
