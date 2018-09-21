package brownshome.netcode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brownshome.netcode.packets.BaseNetworkSchema;
import brownshome.netcode.packets.SchemaPacket;

/** This class represents a schema of protocols. It is negotiated separately for each connection. */
public final class GlobalNetworkProtocol {
	private final List<NetworkSchema> schemas;
	private final Map<String, NetworkSchema> nameMapping = new HashMap<>();
	private final Map<Class<? extends Packet>, PacketDefinition<?>> definitions = new HashMap<>();
	
	public GlobalNetworkProtocol(List<NetworkSchema> schemas) {
		this.schemas = schemas;
		
		addSchema(BaseNetworkSchema.singleton());
		
		for(NetworkSchema schema : schemas) {
			addSchema(schema);
		}
	}
	
	private void addSchema(NetworkSchema schema) {
		nameMapping.put(schema.getFullName(), schema);
		
		for(PacketDefinition<? extends Packet> definition : schema.getPacketTypes()) {
			definitions.put(definition.type, definition);
		}
	}
	
	public SchemaPacket getConnectPacket() {
		return new SchemaPacket(schemas);
	}
	
	public NetworkSchema getSchema(String name) {
		return nameMapping.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Packet> PacketDefinition<? extends T> getDefinition(T packet) {
		return (PacketDefinition<? extends T>) definitions.get(packet.getClass());
	}
}
