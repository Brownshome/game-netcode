package brownshome.netcode.packets;

import java.nio.ByteBuffer;

import javax.annotation.processing.Generated;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;

@Generated("")
public final class NegotiationFailedPacket extends Packet {
	private final String reason;
	
	public NegotiationFailedPacket(String reason) {
		super(BaseSchema.FULL_NAME, "default", 3);
		
		this.reason = reason;
	}
	
	protected NegotiationFailedPacket(ByteBuffer data) {
		this(NetworkUtils.readString(data));
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		NetworkUtils.writeString(buffer, reason);
	}

	@Override
	public int size() {
		return NetworkUtils.calculateSize(reason).size();
	}
	
	@Override
	public boolean isSizeConstant() {
		return false;
	}
	
	@Override
	public boolean isSizeExact() {
		return false;
	}

	@Override
	public void handle(Connection<?> connection, int minorVersion) throws NetworkException {
		BasePackets.negotiationFailed(connection, reason);
	}
}
