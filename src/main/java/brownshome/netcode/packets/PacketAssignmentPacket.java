package brownshome.netcode.packets;

import java.nio.ByteBuffer;
import java.util.List;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkProtocol;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;
import brownshome.netcode.annotation.Reliable;
import brownshome.netcode.packets.dto.NetworkSchemaDTO;

/**
 * This packet returns a list of schemas, with the packets and version numbers edited
 * to be the best fit between the two clients.
 * @author james
 */
@PacketType("packet-assignment")
@Reliable
public class PacketAssignmentPacket extends Packet {
	private final List<NetworkSchemaDTO> compatibleSchema;
	private final int size;
	
	public PacketAssignmentPacket(ByteBuffer buffer) {
		size = buffer.remaining();
		compatibleSchema = NetworkUtils.readList(buffer, NetworkSchemaDTO::new);
	}
	
	public PacketAssignmentPacket(List<NetworkSchemaDTO> compatibleSchema) {
		size = compatibleSchema.stream().mapToInt(NetworkSchemaDTO::size).sum() + Integer.SIZE;
		this.compatibleSchema = compatibleSchema;
	}

	@Override
	public void writeTo(ByteBuffer buffer) {
		NetworkUtils.writeCollection(buffer, compatibleSchema, (buf, t) -> t.writeTo(buf));
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void handle(Connection<?> connection) {
		connection.setProtocol(new NetworkProtocol(connection, compatibleSchema));
	}
}
