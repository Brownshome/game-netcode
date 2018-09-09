package brownshome.netcode.packets;

import java.nio.ByteBuffer;

import brownshome.netcode.Connection;
import brownshome.netcode.Packet;
import brownshome.netcode.annotation.PacketType;

import brownshome.netcode.generated.GameNetworkSchema;

@PacketType("Hello")
public class HelloPacket extends Packet {
	@Override
	public void writeTo(ByteBuffer buffer) {  }

	@Override
	public void readFrom(ByteBuffer buffer) {  }

	@Override
	public void handle(Connection connection) {
		connection.send(new HelloPacket());
		System.out.println("Hello!");
		
		new GameNetworkSchema();
	}
}
