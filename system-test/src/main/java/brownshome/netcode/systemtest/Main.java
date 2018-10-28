package brownshome.netcode.systemtest;

import java.io.IOException;
import java.util.List;

import brownshome.netcode.BaseSchema;
import brownshome.netcode.Connection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.Schema;
import brownshome.netcode.systemtest.packets.TestMessagePacket;
import brownshome.netcode.systemtest.packets.TestSchema;
import brownshome.netcode.udp.UDPConnectionManager;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		List<Schema> protocol = List.of(new BaseSchema(), new TestSchema());

		UDPConnectionManager clientConnectionManager = new UDPConnectionManager(protocol);
		UDPConnectionManager serverConnectionManager = new UDPConnectionManager(protocol);

		serverConnectionManager.registerExecutor("default", Runnable::run);
		clientConnectionManager.registerExecutor("default", Runnable::run);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager.getAddress());

		try {
			connection.connectSync();
		} catch(NetworkException e) {
			throw new RuntimeException("Unable to connect to server", e);
		}

		connection.send(new TestMessagePacket("Hello"));

		clientConnectionManager.close();
		serverConnectionManager.close();
	}
}
