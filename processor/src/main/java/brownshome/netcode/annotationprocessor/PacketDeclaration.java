package brownshome.netcode.annotationprocessor;

import brownshome.netcode.annotation.NetworkDirection.Sender;

/**
 * Stores all of the data to define a packet.
 */
public final class PacketDeclaration {
	private final String name;
	private final int priority;
	private final boolean canFragment;
	private final boolean isReliable;
	private final Sender sender;
	private final String handledBy;
	private final String className;
	private final String schema;
	
	public PacketDeclaration(String schema, String name, int priority, boolean canFragment, boolean isReliable, 
			Sender sender, String handledBy, String className) {
		
		this.name = name;
		this.priority = priority;
		this.canFragment = canFragment;
		this.isReliable = isReliable;
		this.sender = sender;
		this.handledBy = handledBy;
		this.className = className;
		this.schema = schema;
	}
	
	public String getSchema() {
		return schema;
	}
	
	public String getDefinition() {
		return String.format(
				"new PacketDefinition<>(\"%s\", %d, %b, %b, Sender.%s, \"%s\", %s::new, %s.class)",
				name, priority, canFragment, isReliable, sender, handledBy, className, className);
	}
}
