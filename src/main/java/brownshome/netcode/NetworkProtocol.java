package brownshome.netcode;

import java.util.HashMap;
import java.util.Map;

public final class NetworkProtocol {
	private final Connection<?> connection;
	private final Map<NetworkSchema, Integer> schemaMinorVersion = new HashMap<>();
	private final Map<Class<? extends Packet>, Integer> packetId = new HashMap<>();
	
	public NetworkProtocol(Connection<?> connection) {
		this.connection = connection;
	}
	
	public boolean isSchemaSupported(NetworkSchema schema) {
		return schemaMinorVersion.containsKey(schema);
	}

	public boolean isPacketSupported(Packet packet) {
		return packetId.containsKey(packet.getClass());
	}

	public int getMinorVersion(NetworkSchema schema) {
		if(!isSchemaSupported(schema)) {
			throw new MismatchedProtocolException(schema, connection);
		}
		
		return schemaMinorVersion.get(schema);
	}
	
	int getIdForPacket(Packet packet) {
		if(!isPacketSupported(packet)) {
			throw new MismatchedProtocolException(packet, connection);
		}
		
		return packetId.get(packet.getClass());
	}
}
