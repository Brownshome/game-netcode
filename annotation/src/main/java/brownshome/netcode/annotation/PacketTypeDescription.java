package brownshome.netcode.annotation;

public final class PacketTypeDescription {
	public final String name;
	public final int priority;
	public final boolean canFragment;
	public final boolean isReliable;
	public final NetworkDirection.Sender sender;
	public final String handledBy;
	
	public PacketTypeDescription(String name, int priority, boolean canFragment, boolean isReliable, 
			NetworkDirection.Sender sender, String handledBy) {
		
		this.name = name;
		this.priority = priority;
		this.canFragment = canFragment;
		this.isReliable = isReliable;
		this.sender = sender;
		this.handledBy = handledBy;
	}
}
