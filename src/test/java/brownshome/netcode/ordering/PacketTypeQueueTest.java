package brownshome.netcode.ordering;

import brownshome.netcode.Packet;
import brownshome.netcode.testpackets.HelloPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class PacketTypeQueueTest {
	private OrderingManager manager;
	private BlockingQueue<Packet> outputQueue;
	private PacketTypeQueue packetQueue;

	@BeforeEach
	void setUp() {
		outputQueue = new LinkedBlockingQueue<>();
		manager = new OrderingManager(outputQueue);
		packetQueue = new PacketTypeQueue(manager);
	}

	@Test
	public void testDispatchOfUnrelatedPackets() {
		SequencedPacket simplePacket = new SequencedPacket(new HelloPacket(), 1);
		packetQueue.add(simplePacket);

		assertEquals(outputQueue.size(), 1);
		assertSame(outputQueue.element(), simplePacket.packet());
	}

	@Test
	public void add() {
	}

	@Test
	public void notifyExecutionFinished() {
	}

	@Test
	public void trim() {
	}

	@Test
	public void oldestPacket() {
	}
}