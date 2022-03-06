package brownshome.netcode.util;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import brownshome.netcode.Packet;

public final class PacketTypeMap {
	public record PacketType(int id, BitSet waitsFor) {
		PacketType(int id) {
			this(id, null);
		}

		public boolean isComplete() {
			return waitsFor != null;
		}

		private PacketType complete(BitSet waits) {
			assert !isComplete();

			return new PacketType(id, waits);
		}
	}

	private final AtomicInteger nextId;
	private final Map<Class<? extends Packet>, PacketType> types = new ConcurrentHashMap<>();

	public PacketTypeMap(int firstId) {
		this.nextId = new AtomicInteger(firstId);
	}

	public PacketType getType(Packet packet) {
		return types.compute(packet.getClass(), (c, existingType) -> {
			if (existingType == null) {
				existingType = makeIncompleteType();
			}

			if (!existingType.isComplete()) {
				var waits = new BitSet();
				for (var o : packet.orderedBy()) {
					waits.set(o.equals(c)
							? existingType.id
							: getType(o).id);
				}

				existingType = existingType.complete(waits);
			}

			return existingType;
		});
	}

	private PacketType getType(Class<? extends Packet> c) {
		return types.computeIfAbsent(c, unused -> makeIncompleteType());
	}

	private PacketType makeIncompleteType() {
		return new PacketType(nextId.getAndIncrement());
	}
}
