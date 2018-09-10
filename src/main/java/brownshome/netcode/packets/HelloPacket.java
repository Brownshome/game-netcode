package brownshome.netcode.packets;

import java.nio.ByteBuffer;

import brownshome.netcode.Connection;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;

@PacketType("Hello")
public class HelloPacket extends Packet {
	HelloPacket(ByteBuffer buffer) {  }
	public HelloPacket() {  }
	
	@Override
	public void writeTo(ByteBuffer buffer) {  }

	@Override
	public void handle(Connection<?> connection) {
		System.out.println("Hello!");
		connection.send(new HelloPacket());
	}
	
	@Override
	public int size() {
		return 0;
	}
}
