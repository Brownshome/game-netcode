package brownshome.netcode.ordering;

import brownshome.netcode.Packet;
import brownshome.netcode.testpackets.SelfOrderedPacket;
import brownshome.netcode.testpackets.SimplePacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PacketTypeQueueTest {
	private OrderingManager manager;

	private List<Packet> executed;
	private List<Packet> dropped;

	@BeforeEach
	void setUp() {
		executed = new ArrayList<>();
		dropped = new ArrayList<>();

		manager = new OrderingManager(executed::add, dropped::add);
	}

	/**
	 * Tests that all packets are dispatched when they are not related
	 */
	@Test
	public void testDispatchOfUnrelatedPackets() {
		SequencedPacket a = new SequencedPacket(new SimplePacket("a"), 2);
		SequencedPacket b = new SequencedPacket(new SimplePacket("b"), 3);
		SequencedPacket c = new SequencedPacket(new SimplePacket("c"), -1);

		manager.deliverPacket(a);
		manager.deliverPacket(b);
		manager.deliverPacket(c);

		assertEquals(executed, List.of(a.packet(), b.packet(), c.packet()));
		assertEquals(dropped, Collections.emptyList());
	}

	/**
	 * Tests that the ordering system drops the correct packets when given incomplete information
	 */
	@Test
	public void testDispatchOfOrderedPackets() {
		SequencedPacket a = new SequencedPacket(new SelfOrderedPacket("a"), 2);
		SequencedPacket b = new SequencedPacket(new SelfOrderedPacket("b"), 3);
		SequencedPacket c = new SequencedPacket(new SelfOrderedPacket("c"), -1);

		manager.deliverPacket(a);
		manager.deliverPacket(b);
		manager.deliverPacket(c);

		assertEquals(executed, List.of(a.packet()));
		assertEquals(dropped, List.of(c.packet()));

		manager.notifyExecutionFinished(a.packet());

		assertEquals(executed, List.of(a.packet(), b.packet()));
		assertEquals(dropped, List.of(c.packet()));
	}

	/**
	 * Tests that the ordering system does not drop any packets if they are pre-warned.
	 */
	@Test
	public void testDispatchOfOrderedPacketsWithPreNotify() {
		SequencedPacket a = new SequencedPacket(new SelfOrderedPacket("a"), 2);
		SequencedPacket b = new SequencedPacket(new SelfOrderedPacket("b"), 3);
		SequencedPacket c = new SequencedPacket(new SelfOrderedPacket("c"), -1);

		manager.preDeliverPacket(a);
		manager.preDeliverPacket(b);
		manager.preDeliverPacket(c);

		manager.deliverPacket(a);
		manager.deliverPacket(b);
		manager.deliverPacket(c);

		assertEquals(executed, List.of(c.packet()));
		assertEquals(dropped, Collections.emptyList());
		manager.notifyExecutionFinished(c.packet());

		assertEquals(executed, List.of(c.packet(), a.packet()));
		assertEquals(dropped, Collections.emptyList());
		manager.notifyExecutionFinished(a.packet());

		assertEquals(executed, List.of(c.packet(), a.packet(), b.packet()));
		assertEquals(dropped, Collections.emptyList());
		manager.notifyExecutionFinished(b.packet());

		assertEquals(executed, List.of(c.packet(), a.packet(), b.packet()));
		assertEquals(dropped, Collections.emptyList());
	}

	/**
	 * Test code that uses the trim function in a valid manor
	 */
	@Test
	public void testValidTrim() {
		SequencedPacket a = new SequencedPacket(new SelfOrderedPacket("a"), -50);
		SequencedPacket b = new SequencedPacket(new SelfOrderedPacket("b"), 2);
		SequencedPacket c = new SequencedPacket(new SelfOrderedPacket("c"), -1);

		manager.preDeliverPacket(c);

		manager.trimPacketNumbers(-60);

		manager.deliverPacket(a);               //A is PROCESSING
		manager.deliverPacket(b);               //B is RECEIVED
		manager.notifyExecutionFinished(a.packet());
		manager.deliverPacket(c);               //C is RECEIVED

		manager.trimPacketNumbers(50);


		manager.notifyExecutionFinished(c.packet());

		assertEquals(executed, List.of(a.packet(), c.packet(), b.packet()));
		assertEquals(dropped, Collections.emptyList());
	}

	/**
	 * Test code that drops packets on the pre-receive method.
	 */
	@Test
	public void testDropPacketOnPreReceive() {
		SequencedPacket a = new SequencedPacket(new SelfOrderedPacket("a"), 1);
		SequencedPacket b = new SequencedPacket(new SelfOrderedPacket("b"), 2);

		manager.deliverPacket(b);
		manager.preDeliverPacket(a);

		assertEquals(executed, List.of(b.packet()));
		assertEquals(dropped, List.of(a.packet()));
	}
}