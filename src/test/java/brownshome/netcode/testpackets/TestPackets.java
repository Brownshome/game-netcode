package brownshome.netcode.testpackets;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.MakeOrdered;

import java.util.logging.Logger;

/** This class contains all of the packets used by the unit tests. */
final class TestPackets {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode.test");

	private TestPackets() {  }

	@DefinePacket(name = "Simple")
	static void simple(String name) {	}

	@DefinePacket(name = "SelfOrdered")
	@MakeOrdered("SelfOrdered")
	static void selfOrdered(String name) {	}
}
