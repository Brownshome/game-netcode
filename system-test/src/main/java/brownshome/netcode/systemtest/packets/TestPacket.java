package brownshome.netcode.systemtest.packets;

import java.util.logging.Logger;

import brownshome.netcode.annotation.DefinePacket;

public class TestPacket {
	private static final Logger LOGGER = Logger.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	protected void recievePacket(String message) {
		LOGGER.info("Recieved message: " + message);
	}
}
