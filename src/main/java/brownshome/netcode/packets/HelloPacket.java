package brownshome.netcode.packets;

import java.nio.ByteBuffer;

import javax.annotation.processing.Generated;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.Packet;

@Generated("")
public final class HelloPacket extends Packet {
	private final int numberOfWaves;
	
	public HelloPacket(int numberOfWaves) {
		super(BaseSchema.FULL_NAME, "default", 0);
		
		this.numberOfWaves = numberOfWaves;
	}
	
	protected HelloPacket(ByteBuffer data) {
		this(data.getInt());
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		buffer.putInt(numberOfWaves);
	}

	@Override
	public int size() {
		return Integer.BYTES;
	}

	@Override
	public void handle(Connection<?> connection, int minorVersion) throws NetworkException {
		BasePackets.sayHelloBack(connection, numberOfWaves);
	}
}
