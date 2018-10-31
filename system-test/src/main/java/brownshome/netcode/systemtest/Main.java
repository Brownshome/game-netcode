package brownshome.netcode.systemtest;

import java.io.*;
import java.util.*;

import brownshome.netcode.*;
import brownshome.netcode.systemtest.packets.*;
import brownshome.netcode.udp.UDPConnectionManager;

public class Main {
	public static void main(String[] args) throws InterruptedException, IOException {
		List<Schema> protocol = List.of(new BaseSchema(), new TestSchema());

		UDPConnectionManager clientConnectionManager = new UDPConnectionManager(protocol);
		UDPConnectionManager serverConnectionManager = new UDPConnectionManager(protocol);

		serverConnectionManager.registerExecutor("default", Runnable::run);
		clientConnectionManager.registerExecutor("default", Runnable::run);
		
		Connection<?> connection = clientConnectionManager.getOrCreateConnection(serverConnectionManager.getAddress());

		connection.connectSync();

		connection.send(new TestMessagePacket("Hello"));

		clientConnectionManager.close();
		serverConnectionManager.close();
	}
}
