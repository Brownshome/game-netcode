package brownshome.netcode;

import java.nio.ByteBuffer;

import brownshome.netcode.annotation.*;
import brownshome.netcode.annotation.NetworkDirection.Sender;

/** Defines a packet. This packet has two methods that write and read from the
 * data stream. Subclasses of this class must define a no-argument constructor
 * that will be called by the net-code system. */
@NetworkDirection(Sender.BOTH)
@Priority(0)
@HandledBy("DefaultHandler")
public abstract class Packet {
	public abstract void writeTo(ByteBuffer buffer);
	public abstract void readFrom(ByteBuffer buffer);
	
	public abstract void handle(Connection connection);
}
