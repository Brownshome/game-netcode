package brownshome.netcode.systemtest.packets;

import java.util.List;
import java.util.logging.Logger;

import brownshome.netcode.annotation.DefinePacket;

public class TestPacket {
	private static final Logger LOGGER = Logger.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	protected void receivePacket(String message) {
		LOGGER.info("Received message: " + message);
	}

	@DefinePacket(name = "NestedListTest")
	protected void listOfList(List<List<String>> listOfList) {

	}
}
