package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

import java.util.Objects;

/**
 * This is a struct that is used to denote a single type of packets
 */
public class PacketType {
	final String schemaName;
	final int id;

	PacketType(String schemaName, int id) {
		this.schemaName = schemaName;
		this.id = id;
	}

	PacketType(Packet packet) {
		this(packet.schemaName(), packet.packetID());
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		PacketType that = (PacketType) o;
		return id == that.id && schemaName.equals(that.schemaName);
	}

	@Override
	public int hashCode() {
		return schemaName.hashCode() * 31 + id;
	}
}
