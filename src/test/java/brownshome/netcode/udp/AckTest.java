package brownshome.netcode.udp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AckTest {
	@Test
	void testAcksWithNoAcks() {
		Ack ack = new Ack(0, 0);
		assertEquals(0, ack.ackedPackets.length);
	}

	@Test
	void testAcks() {
		Ack ack = new Ack(50, 0b11010);
		assertArrayEquals(new int[] { 49, 47, 46 }, ack.ackedPackets);
	}
}