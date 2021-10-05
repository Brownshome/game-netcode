package brownshome.netcode;

import brownshome.netcode.annotation.converter.Networkable;

/**
 * This class is extended by generated packet classes. It will usually not be needed to be implemented manually.
 **/
public abstract class Packet implements Networkable {
	private final String handledBy;
	private final String schemaName;
	private final int packetId;
	private final int[] orderedIds;
	
	protected Packet(String schemaName, String handler, int packetId, int[] orderedIds) {
		this.schemaName = schemaName;
		this.handledBy = handler;
		this.packetId = packetId;
		this.orderedIds = orderedIds;
	}
	
	/** 
	 * Handles the packet. This method will handle the packet on this thread. The version number may be used to enforce
	 * compatibility with older versions.
	 **/
	public abstract void handle(Connection<?> connection, int minorVersion) throws NetworkException;
	
	/**
	 * Returns an ID for this packet. This ID must be unique per schema. The IDs should be allocated starting from 0 incrementing for each packet.
	 * @return the id
	 **/
	public final int packetID() {
		return packetId;
	}
	
	/**
	 * The long name of the schema that this packet belongs to.
	 * @return the schema name
	 **/
	public final String schemaName() {
		return schemaName;
	}

	/**
	 * The name of the handler that will handle this packet.
	 * @return the name of the handler method
	 **/
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
	
	/**
	 * The set of packet IDs that this packet may not overtake.
	 * @return an array of packet IDs
	 */
	public final int[] orderedIds() {
		return orderedIds;
	}
	
	/**
	 * Whether this packet will always reach the endpoint.
	 * @return true if this packet is marked as reliable
	 **/
	public boolean reliable() {
		return false;
	}
	
	/**
	 * The priority of this packet, higher priorities will be sent first.
	 * @return the priority number
	 **/
	public int priority() {
		return 0;
	}
}
