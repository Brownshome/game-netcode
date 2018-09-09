package brownshome.netcode.annotation;

/**
 * Stores all of the data to define a packet.
 */
public final class PacketDeclaration {
	private final String name;
	private final int priority;
	private final boolean canFragment;
	private final boolean isReliable;
	private final NetworkDirection.Sender sender;
	private final String handledBy;
	private final String className;
	
	public PacketDeclaration(String name, int priority, boolean canFragment, boolean isReliable, 
			NetworkDirection.Sender sender, String handledBy, String className) {
		
		this.name = name;
		this.priority = priority;
		this.canFragment = canFragment;
		this.isReliable = isReliable;
		this.sender = sender;
		this.handledBy = handledBy;
		this.className = className;
	}
	
	public String getClassName() {
		return className;
	}
	
	public String getName() {
		return name;
	}
	
	public int getPriority() {
		return priority;
	}
	
	public boolean isCanFragment() {
		return canFragment;
	}
	
	public boolean isReliable() {
		return isReliable;
	}
	
	public NetworkDirection.Sender getSender() {
		return sender;
	}
	
	public String getHandledBy() {
		return handledBy;
	}
}
