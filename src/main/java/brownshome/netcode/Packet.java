package brownshome.netcode;

import brownshome.netcode.annotation.converter.Networkable;

import java.util.Collection;

/**
 * This class is extended by generated packet classes. It will usually not be needed to be implemented manually.
 **/
public abstract class Packet implements Networkable {
	private final int packetId;
	private final Class<? extends Schema> schema;
	private final Collection<Class<? extends Packet>> orderedBy;
	
	protected Packet(Class<? extends Schema> schema, int packetId, Collection<Class<? extends Packet>> orderedBy) {
		this.schema = schema;
		this.packetId = packetId;
		this.orderedBy = orderedBy;
	}
	
	/** 
	 * Handles the packet. This method will handle the packet on this thread. The schema object can be used to query the
	 * minor and major versions of the negotiated schema.
	 *
	 * @param connection the connection that this packet was received on
	 * @param schema the schema that is currently being used for this connection
	 **/
	public abstract void handle(Connection<?> connection, Schema schema) throws NetworkException;
	
	/**
	 * Returns an ID for this packet. This ID must be unique per schema. The IDs should be allocated starting from 0 incrementing for each packet.
	 * @return the id
	 **/
	public final int packetID() {
		return packetId;
	}
	
	/**
	 * The class of the schema that this packet is a part of
	 **/
	public final Class<? extends Schema> schema() {
		return schema;
	}

	/**
	 * The set of packet IDs that this packet may not overtake.
	 * @return an array of packet IDs
	 */
	public final Collection<Class<? extends Packet>> orderedBy() {
		return orderedBy;
	}
	
	/**
	 * Whether this packet will always reach the endpoint.
	 * @return true if this packet is marked as reliable
	 **/
	public abstract boolean reliable();
	
	/**
	 * The priority of this packet, higher priorities will be sent first.
	 * @return the priority number
	 **/
	public abstract int priority();

	/**
	 * The minimum minor version that this packet implementation can be used with.
	 * @return the minor version
	 */
	public abstract int minimumMinorVersion();
}
