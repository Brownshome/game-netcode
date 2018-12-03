package brownshome.netcode.systemtest.packets;

import java.util.Random;
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
		long ans = 0;
		Random random = new Random();
		for(long l = 0; l < 1_000_000_000L; l++) {
			ans += l * l + 5 + ans * random.nextLong();
		}

		LOGGER.info("Executed long packet " + ans);
	}
}
