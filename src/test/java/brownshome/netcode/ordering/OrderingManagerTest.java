package brownshome.netcode.ordering;

import brownshome.netcode.Packet;
import brownshome.netcode.testpackets.SimplePacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderingManagerTest {
	private OrderingManager manager;

	private List<Packet> executed;
	private List<Packet> dropped;

	@BeforeEach
	void setUp() {
		executed = new ArrayList<>();
		dropped = new ArrayList<>();

		manager = new OrderingManager(executed::add, dropped::add);
	}

	@Test
	void testDeliverInvalidPacket() {
		//These sequence numbers are too largely spaced out to have a valid ordering
		SequencedPacket a = new SequencedPacket(new SimplePacket("a"), Integer.MAX_VALUE / 3 * 2);
		SequencedPacket b = new SequencedPacket(new SimplePacket("b"), 0);
		SequencedPacket c = new SequencedPacket(new SimplePacket("c"), Integer.MIN_VALUE / 3 * 2);

		manager.deliverPacket(a);
		manager.deliverPacket(b);

		try {
			manager.deliverPacket(c);
			fail("");
		} catch(IllegalArgumentException expected) {  }

		try {
			manager.preDeliverPacket(c);
			fail("");
		} catch(IllegalArgumentException expected) {  }
	}
}