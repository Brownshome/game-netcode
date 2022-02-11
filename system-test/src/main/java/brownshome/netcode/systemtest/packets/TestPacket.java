package brownshome.netcode.systemtest.packets;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.Reliable;

public class TestPacket {
	private static final System.Logger LOGGER = System.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	@Reliable
	void receivePacket(String message) {
		LOGGER.log(System.Logger.Level.INFO, "Received message: " + message);
	}

	@DefinePacket(name = "LongProcessing")
	@Reliable
	void longProcessing(long millis) {
		try {
			Thread.sleep(millis);
		} catch(InterruptedException e) {  }

		LOGGER.log(System.Logger.Level.INFO, "Executed long packet");
	}

	@DefinePacket(name = "CauseError")
	@Reliable
	void causeError() {
		throw new RuntimeException();
	}
}
