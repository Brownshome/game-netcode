package brownshome.netcode.packets.dto;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;

import brownshome.netcode.NetworkSchema;
import brownshome.netcode.NetworkUtils;

public final class NetworkSchemaDTO {
	public final String name;
	public final int minor, major;
	public final List<String> packetNames;
	
	public NetworkSchemaDTO(ByteBuffer buffer) {
		this(
				NetworkUtils.readString(buffer), 
				buffer.getInt(), 
				buffer.getInt(), 
				NetworkUtils.readList(buffer, NetworkUtils::readString)
		);
	}
	
	public NetworkSchemaDTO(NetworkSchema schema) {
		this(
				schema.getFullName(), 
				schema.getMajorVersion(), 
				schema.getMinorVersion(), 
				schema.getPacketTypes().stream().map(def -> def.name).collect(Collectors.toList())
		);
	}
	
	private NetworkSchemaDTO(String name, int major, int minor, List<String> packetNames) {
		this.name = name;
		this.major = major;
		this.minor = minor;
		this.packetNames = packetNames;
	}
	
	public void writeTo(ByteBuffer buffer) {
		NetworkUtils.writeString(buffer, name);
		buffer.putInt(major).putInt(minor);
		NetworkUtils.writeCollection(buffer, packetNames, NetworkUtils::writeString);
	}
	
	public int size() {
		return Integer.BYTES + NetworkUtils.calculateLength(name)
				+ Integer.BYTES + Integer.BYTES
				+ Integer.BYTES + packetNames.stream().mapToInt(NetworkUtils::calculateLength).sum();
	}
}