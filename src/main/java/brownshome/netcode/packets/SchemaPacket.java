package brownshome.netcode.packets;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import brownshome.netcode.Connection;
import brownshome.netcode.GlobalNetworkProtocol;
import brownshome.netcode.NetworkException;
import brownshome.netcode.NetworkSchema;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;
import brownshome.netcode.PacketDefinition;
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
		List<NetworkSchemaDTO> compatibleSchema = new ArrayList<>();
		
		NetworkSchemaDTO baseCompatability = compatable(baseSchema, BaseNetworkSchema.singleton());
		
		if (baseCompatability == null) {
			throw new NetworkException("Base modules are not compatible", connection);
		}
		
		compatibleSchema.add(baseCompatability);
		
		GlobalNetworkProtocol globalProtocol = connection.getConnectionManager().getGlobalProtocol();
		for(NetworkSchemaDTO other : otherSchema) {
			NetworkSchema schema = globalProtocol.getSchema(other.name);
			
			if(schema != null) {
				NetworkSchemaDTO c = compatable(other, schema);
				if (c != null) {
					compatibleSchema.add(c);
				}
			}
		}
		
		PacketAssignmentPacket packet = new PacketAssignmentPacket(compatibleSchema);
		packet.handle(connection);
		connection.send(packet);
		
		NetworkUtils.LOGGER.info("Received schema");
	}

	private NetworkSchemaDTO compatable(NetworkSchemaDTO remoteSchema, NetworkSchema localSchema) {
		if(remoteSchema.major != localSchema.getMajorVersion()) {
			return null;
		}
		
		Set<String> remotePackets = new HashSet<>(remoteSchema.packetNames);
		List<String> packets = new ArrayList<>();
		
		for(PacketDefinition<?> def : localSchema.getPacketTypes()) {
			String name = def.type.getName();
			
			if(remotePackets.contains(name))
				packets.add(name);
		}
		
		return new NetworkSchemaDTO(remoteSchema.name, 
				remoteSchema.major, 
				Math.min(remoteSchema.minor, localSchema.getMinorVersion()),
				packets);
	}

	@Override
	public int size() {
		return size;
	}
}
