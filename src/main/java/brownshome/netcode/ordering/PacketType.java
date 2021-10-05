package brownshome.netcode.ordering;

import brownshome.netcode.Packet;

import java.util.Objects;

/**
 * This is a record that is used to denote a single type of packets
 * @param id the packet id that this packet type uses
 * @param schemaName the name of the schema that this packet uses
 */
public record PacketType(String schemaName, int id) {
	public PacketType(Packet packet) {
		this(packet.schemaName(), packet.packetID());
	}
}
