package brownshome.netcode.systemtest.packets;

import java.util.logging.Logger;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.MakeOrdered;

public class TestPacket {
	private static final Logger LOGGER = Logger.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	protected void receivePacket(String message) {
		LOGGER.info("Received message: " + message);
	}

	@DefinePacket(name = "LongProcessing")
	@MakeOrdered("LongProcessing")
	protected void longProcessing(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {  }
	}
}
