package brownshome.netcode;

import java.nio.ByteBuffer;
import java.util.function.Function;

import brownshome.netcode.annotation.NetworkDirection.Sender;

public final class PacketDefinition<PACKET extends Packet> {
	public final String name;
	public final int priority;
	public final boolean canFragment;
	public final boolean isReliable;
	public final Sender sender;
	public final String handledBy;
	public final Function<ByteBuffer, PACKET> constructor;
	public final Class<PACKET> type;
	
	public PacketDefinition(String name, int priority, boolean canFragment, boolean isReliable, Sender sender,
			String handledBy, Function<ByteBuffer, PACKET> constructor, Class<PACKET> type) {
		
		this.name = name;
		this.priority = priority;
		this.canFragment = canFragment;
		this.isReliable = isReliable;
		this.sender = sender;
		this.handledBy = handledBy;
		this.constructor = constructor;
		this.type = type;
	}
}