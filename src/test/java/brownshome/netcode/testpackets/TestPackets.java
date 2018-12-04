package brownshome.netcode.testpackets;

import brownshome.netcode.annotation.DefinePacket;

import java.util.logging.Logger;

/** This class contains all of the packets used by the unit tests. */
public final class TestPackets {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode.test");

	private TestPackets() {  }

	@DefinePacket(name = "Hello")
	public static void hello() {
		LOGGER.info("Hello called");
	}
}
