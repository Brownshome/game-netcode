package brownshome.netcode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import brownshome.netcode.packets.dto.NetworkSchemaDTO;

public final class NetworkProtocol {
	private final Connection<?> connection;
	private final Map<NetworkSchema, Integer> schemaMinorVersion = new HashMap<>();
	private final Map<Class<? extends Packet>, Integer> packetId = new HashMap<>();
	
	@SuppressWarnings("unchecked")
	public NetworkProtocol(Connection<?> connection, List<NetworkSchemaDTO> compatibleSchema) {
		this.connection = connection;
		
		int id = 0;
		GlobalNetworkProtocol global = connection.getConnectionManager().getGlobalProtocol();
		for(NetworkSchemaDTO dto : compatibleSchema) {
			schemaMinorVersion.put(global.getSchema(dto.name), dto.minor);
			
			for(String packet : dto.packetNames) {
				try {
					packetId.put((Class<? extends Packet>) Class.forName(packet), Integer.valueOf(id++));
				} catch (ClassNotFoundException e) {
					throw new NetworkException("Invalid schema handshake: " + e.getMessage(), connection);
				}
			}
		}
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
