package brownshome.netcode.packets;

import java.nio.ByteBuffer;

import javax.annotation.processing.Generated;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.Packet;
import brownshome.netcode.Protocol;
import brownshome.netcode.annotation.WithDirection.Direction;

@Generated("")
public final class ConfirmProtocolPacket extends Packet {
	private final Protocol protocol;
	
	public ConfirmProtocolPacket(Protocol protocol) {
		super(BaseSchema.FULL_NAME, "default", 2);
		
		this.protocol = protocol;
	}
	
	protected ConfirmProtocolPacket(ByteBuffer data) {
		this(new Protocol(data));
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		protocol.write(buffer);
	}

	@Override
	public int size() {
		return protocol.size();
	}

	@Override
	public boolean isSizeConstant() {
		return protocol.isSizeConstant();
	}
	
	@Override
	public boolean isSizeExact() {
		return protocol.isSizeExact();
	}
	
	@Override
	public Direction direction() {
		return Direction.TO_CLIENT;
	}
	
	@Override
	public void handle(Connection<?> connection, int minorVersion) throws NetworkException {
		BasePackets.confirmProtocol(connection, protocol);
	}
}
