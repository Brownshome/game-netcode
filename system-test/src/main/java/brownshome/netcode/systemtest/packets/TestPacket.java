package brownshome.netcode.systemtest.packets;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;

@PacketType("test-packet")
public class TestPacket extends Packet {
	private static final Logger LOGGER = Logger.getLogger("network-test");
	
	private final String msg;
	private final int size;
	
	public TestPacket(ByteBuffer buffer) {
		size = buffer.remaining();
		msg = NetworkUtils.readString(buffer);
	}
	
	public TestPacket(String msg) {
		this.msg = msg;
		size = NetworkUtils.calculateLength(msg);
	}
	
	@Override
	public void writeTo(ByteBuffer buffer) {
		LOGGER.info("Sent message: " + msg);
		NetworkUtils.writeString(buffer, msg);
	}

	@Override
	public void handle(Connection<?> connection) {
		LOGGER.info("Received message: " + msg);
	}

	@Override
	public int size() {
		return size;
	}
}
