package brownshome.netcode.systemtest.packets;

import java.nio.ByteBuffer;

import brownshome.netcode.Connection;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;

@PacketType("test-packet")
public class TestPacket extends Packet {
	public TestPacket(ByteBuffer buffer) {
		// TODO Auto-generated constructor stub
	}
	
	public TestPacket() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void writeTo(ByteBuffer buffer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handle(Connection<?> connection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

}
