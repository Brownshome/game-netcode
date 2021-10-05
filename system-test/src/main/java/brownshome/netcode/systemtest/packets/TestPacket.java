package brownshome.netcode.systemtest.packets;

import java.util.Random;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.MakeOrdered;
import brownshome.netcode.annotation.MakeReliable;

public class TestPacket {
	private static final System.Logger LOGGER = System.getLogger("network-test");
	
	@DefinePacket(name = "TestMessage")
	@MakeReliable
	void receivePacket(String message) {
		LOGGER.log(System.Logger.Level.INFO, "Received message: " + message);
	}

	@DefinePacket(name = "LongProcessing")
	@MakeReliable
	void longProcessing(long millis) {
		try {
			Thread.sleep(millis);
		} catch(InterruptedException e) {  }

		LOGGER.log(System.Logger.Level.INFO, "Executed long packet");
	}

	@DefinePacket(name = "CauseError")
	@MakeReliable
	void causeError() {
		throw new RuntimeException();
	}
}
