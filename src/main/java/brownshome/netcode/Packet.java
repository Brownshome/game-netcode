package brownshome.netcode;

import brownshome.netcode.annotation.converter.Networkable;

/** This class is extended by generated packet classes. It will usually not be needed to be extended manually. */
public abstract class Packet implements Networkable {
	private final String handledBy;
	private final String schemaName;
	private final int packetId;
	
	protected Packet(String schemaName, String handler, int packetId) {
		this.schemaName = schemaName;
		this.handledBy = handler;
		this.packetId = packetId;
	}
	
	/** 
	 * Handles the packet. This method will handle the packet on this thread. The version number may be used to enforce
	 * compatibility with older versions.
	 **/
	public abstract void handle(Connection<?> connection, int minorVersion) throws NetworkException;
	
	/** Returns an ID for this packet. This ID must be unique per schema. The IDs should be allocated starting from 0 incrementing for each packet. */
	public final int packetID() {
		return packetId;
	}
	
	/** Returns the long name of the schema that this packet belongs to. */
	public final String schemaName() {
		return schemaName;
	}

	/** Returns the name of the handler that will handle this packet. */
	public final String handledBy() {
		return handledBy;
	}
	
	@Override
	public boolean isSizeConstant() {
		return true;
	}
	
	@Override
	public boolean isSizeExact() {
		return true;
	}
	
	/** Returns true if this packet can be fragmented. */
	public boolean canFragment() {
		return false;
	}
	
	/** Returns true if this packet will always reach the endpoint. */
	public boolean reliable() {
		return false;
	}
	
	/** Returns the priority of this packet, higher priorities will be sent first. */
	public int priority() {
		return 0;
	}
}
