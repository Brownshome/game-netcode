package brownshome.netcode.udp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class AckSenderTest {
	private Ack decode(AckSender.OutgoingAck ack) {
		return new Ack(ack.largestAck, ack.field);
	}

	@Test
	void testAckSenderWithNoAcks() {
		AckSender sender = new AckSender();
		var ack = sender.createAck();

		assertEquals(0, ack.field);
	}

	@Test
	void testAckSenderWithUnsentAcks() {
		AckSender sender = new AckSender();
		sender.receivedPacket(50);
		sender.receivedPacket(43);
		sender.receivedPacket(46);
		sender.receivedPacket(100);

		var ack = decode(sender.createAck());

		assertArrayEquals(new int[] { 50, 46, 43 }, ack.ackedPackets);

		ack = decode(sender.createAck());

		assertArrayEquals(new int[] { 100 }, ack.ackedPackets);
	}

	@Test
	void testAckSenderWithSentAcks() {

		AckSender sender = new AckSender();
		sender.receivedPacket(50);
		sender.receivedPacket(43);
		sender.receivedPacket(46);

		sender.createAck();
		var ack = decode(sender.createAck());

		assertArrayEquals(new int[] { 50, 46, 43 }, ack.ackedPackets);
	}
}