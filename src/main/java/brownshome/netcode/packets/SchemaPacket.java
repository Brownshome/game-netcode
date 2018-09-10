package brownshome.netcode.packets;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkSchema;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;
import brownshome.netcode.annotation.Reliable;
import brownshome.netcode.packets.dto.NetworkSchemaDTO;

/**
 * This packet sends a list of all of the schema names, along with their version.
 * In addition to that it sends all of the packet names as they are defined.
 * 
 * Packets are assumed to stay the same once named. New packets can be added as long
 * as they are not used with older systems.
 * 
 * @author James Brown
 */
@PacketType("SchemaNegotiation")
@Reliable
public final class SchemaPacket extends Packet {
	private final NetworkSchemaDTO baseSchema;
	private final Collection<NetworkSchemaDTO> otherSchema;
	private final int size;
	
	SchemaPacket(ByteBuffer buffer) {
		size = buffer.remaining();
		
		baseSchema = new NetworkSchemaDTO(buffer);
		otherSchema = new ArrayList<>();
		
		while(buffer.hasRemaining()) {
			otherSchema.add(new NetworkSchemaDTO(buffer));
		}
	}
	
	/** Creates a list of schemas to send. The base schema is special, it is always sent, 
	 * and the first X IDs are always assigned. */
	public SchemaPacket(Collection<NetworkSchema> schemas) {
		this.otherSchema = new ArrayList<>();
		
		baseSchema = new NetworkSchemaDTO(BaseNetworkSchema.singleton());
		
		int tmp = baseSchema.size();
		for(NetworkSchema s : schemas) {
			NetworkSchemaDTO dto = new NetworkSchemaDTO(s);
			tmp += dto.size();
			otherSchema.add(dto);
		}
		
		size = tmp;
	}

	@Override
	public void writeTo(ByteBuffer buffer) {
		baseSchema.writeTo(buffer);
		
		for(NetworkSchemaDTO schema : otherSchema) {
			schema.writeTo(buffer);
		}
	}

	@Override
	public void handle(Connection<?> connection) {
		//Compare all of the schema
		List<String> packetAssignments = new ArrayList<>();
		
		addCompatablePackets(packetAssignments, baseSchema, BaseNetworkSchema.singleton());
		
		NetworkUtils.LOGGER.info("Received schema");
	}

	private void addCompatablePackets(List<String> packetAssignments, NetworkSchemaDTO remoteSchema, NetworkSchema localSchema) {
		
	}

	@Override
	public int size() {
		return size;
	}
}
