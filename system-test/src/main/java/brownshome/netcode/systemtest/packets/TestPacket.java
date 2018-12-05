package brownshome.netcode.systemtest.packets;

import java.util.Random;
import java.util.logging.Logger;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.MakeOrdered;
import brownshome.netcode.annotation.MakeReliable;

public class TestPacket {
	private static final Logger LOGGER = Logger.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	@MakeReliable
	void receivePacket(String message) {
		LOGGER.info("Received message: " + message);
	}

	@DefinePacket(name = "LongProcessing")
	@MakeReliable
	void longProcessing(long millis) {
		try {
			Thread.sleep(millis);
		} catch(InterruptedException e) {  }

		LOGGER.info("Executed long packet");
	}

	@DefinePacket(name = "CauseError")
	@MakeReliable
	void causeError() {
		throw new RuntimeException();
	}
}
