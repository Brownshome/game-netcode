package brownshome.netcode.udp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AckSenderTest {
	@Test
	void testAckSenderEmpty() {
		AckSender sender = new AckSender();

		int field = sender.createAckField(50);
		assertEquals(0, field);
	}

	@Test
	void testAckSenderRollForward() {
		AckSender sender = new AckSender();

		sender.receivedPacket(1);
		sender.receivedPacket(2);
		sender.receivedPacket(3);
		sender.receivedPacket(4);

		int field = sender.createAckField(sender.mostRecentAck() + 1);
		assertEquals(0b1111, field);
	}

	@Test
	void testAckSenderRollOut() {
		AckSender sender = new AckSender();

		sender.receivedPacket(1);
		sender.receivedPacket(2);
		sender.receivedPacket(3);
		sender.receivedPacket(50);

		int field = sender.createAckField(sender.mostRecentAck() + 1);
		assertEquals(0b1, field);
	}

	@Test
	void testAckSenderOutOfRangeAck() {
		AckSender sender = new AckSender();

		sender.receivedPacket(50);
		sender.receivedPacket(0);

		int field = sender.createAckField(sender.mostRecentAck() + 1);
		assertEquals(1, field);
	}

	@Test
	void testAckSenderOldAck() {
		AckSender sender = new AckSender();

		sender.receivedPacket(0);
		sender.receivedPacket(2);

		int field = sender.createAckField(0);
		assertEquals(0, field);
		field = sender.createAckField(1);
		assertEquals(1, field);
		field = sender.createAckField(2);
		assertEquals(2, field);

	}
}