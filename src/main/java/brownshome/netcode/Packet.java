package brownshome.netcode;

import java.nio.ByteBuffer;

import brownshome.netcode.annotation.*;
import brownshome.netcode.annotation.NetworkDirection.Sender;

/** Defines a packet. This packet has two methods that write and read from the
 * data stream. Subclasses of this class must define a single argument constructor
 * that takes a ByteBuffer. */
@NetworkDirection(Sender.BOTH)
@Priority(0)
@HandledBy("DefaultHandler")
public abstract class Packet {
	public abstract void writeTo(ByteBuffer buffer);
	
	/** Returns a number always greater than the packet in size, in bytes. */
	public abstract int size();
	public abstract void handle(Connection<?> connection);
}
