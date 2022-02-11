package brownshome.netcode;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotation.converter.Networkable;

/** 
 * This class represents a collection of Schema that can be used to handle incoming packets.
 * When this class is networked it sends all of the schema that it uses.
 **/
public final class Protocol implements Networkable {
	private static final Protocol BASE_PROTOCOL = new Protocol(List.of(new BaseSchema(0)));

	/** Returns a protocol with only the base schema defined, all systems will support this protocol. */
	public static Protocol baseProtocol() {
		return BASE_PROTOCOL;
	}

	private record SchemaAllocation(int startID, Schema schema) { }

	public record ProtocolNegotiation(Protocol protocol, Set<Schema> missingSchema) {
		public boolean succeeded() {
			return missingSchema.isEmpty();
		}
	}

	public static ProtocolNegotiation negotiateProtocol(List<Schema> requestedList, List<Schema> supportedList) {
		Map<String, Schema> supportedSchemas = new HashMap<>();

		for (Schema s : supportedList) {
			supportedSchemas.put(s.fullName(), s);
		}

		List<Schema> chosenSchema = new ArrayList<>();
		Set<Schema> missingSchema = new HashSet<>();
		
		for (Schema s : requestedList) {
			Schema supported = supportedSchemas.get(s.fullName());

			if (supported == null || supported.majorVersion() != s.majorVersion()) {
				missingSchema.add(s);
			} else {
				int minorVersion = Math.min(s.minorVersion(), supported.minorVersion());

				chosenSchema.add(s.withMinorVersion(minorVersion));
			}
		}

		Protocol protocol = new Protocol(chosenSchema);

		return new ProtocolNegotiation(protocol, missingSchema);
	}

	/** This is ordered by the input order of the schema. */
	private final LinkedHashMap<Class<? extends Schema>, SchemaAllocation> schemaMapping;
	private final Map<Integer, SchemaAllocation> IDToSchemaMapping;
	
	private final int networkSizeData;

	/** The order of the schema does matter. */
	public Protocol(List<Schema> schema) {
		schemaMapping = new LinkedHashMap<>();
		IDToSchemaMapping = new HashMap<>();
		
		int startID = 0;
		
		for (Schema s : schema) {
			SchemaAllocation allocation = new SchemaAllocation(startID, s);
			
			int numberOfSlots = s.numberOfIDsRequired();
			
			assert !schemaMapping.containsKey(s.getClass()) : "Schema " + s.fullName() + " is included twice in the mapping.";
			
			schemaMapping.put(s.getClass(), allocation);
			for (int i = 0; i < numberOfSlots; i++) {
				IDToSchemaMapping.put(startID + i, allocation);
			}
			
			startID += numberOfSlots;
		}
		
		Converter<Schema> converter = new Schema.SchemaConverter();
		networkSizeData = NetworkUtils.calculateSize(schema, converter::size);
	}

	public boolean supports(Schema query) {
		var allocation = schemaMapping.get(query.getClass());
		return allocation != null && allocation.schema.majorVersion() == query.majorVersion()
				&& allocation.schema.minorVersion() >= query.minorVersion();
	}
	
	public int computePacketID(Packet packet) {
		SchemaAllocation allocation = schemaMapping.get(packet.schema());

		assert allocation != null : "Unknown schema " + packet.schema().getName();

		return allocation.startID + packet.packetID();
	}
	
	/**
	 * Decodes a packet into a packet object.
	 * @param buffer The raw data for the packet, with the position at the ID of the packet.
	 * @throws IllegalArgumentException If the ID is not valid, or there is any other error thrown during packet decoding.
	 */
	public Packet createPacket(ByteBuffer buffer) throws IllegalArgumentException {
		int id = buffer.getInt();
		
		SchemaAllocation allocation = IDToSchemaMapping.get(id);
		
		int schemaLocalID;
		
		try {
			schemaLocalID = id - allocation.startID;
		} catch (NullPointerException npe) {
			throw new IllegalArgumentException(String.format("Invalid packet ID: %d", id), npe);
		}
		
		return allocation.schema.createPacket(schemaLocalID, buffer);
	}

	/**
	 * Executes a given packet
	 * @param connection the connection the packet came from
	 * @param packet the packet to execute
	 * @throws NetworkException if there is an error executing the packet
	 */
	public void handle(Connection<?, ?> connection, Packet packet) throws NetworkException {
		Schema schema = schemaMapping.get(packet.schema()).schema;

		if (schema.minorVersion() < packet.minimumMinorVersion()) {
			throw new NetworkException("Invalid packet ID (minor version mismatch, %d < %d)"
					.formatted(schema.minorVersion(), packet.minimumMinorVersion()), connection);
		}

		packet.handle(connection, schema);
	}

	@Override
	public String toString() {
		return String.format("Protocol %s", schemaMapping.values()
				.stream().map(alloc -> alloc.schema).collect(Collectors.toList()));
	}

	/* ****************** NETWORKABLE ****************** */
	
	/**
	 * This constructor creates a protocol from an incoming packet of data.
	 **/
	public Protocol(ByteBuffer buffer) {
		//Sorry about the one-liner, Java requires that this is the first statement in a constructor #SwiftIsBetter
		this(NetworkUtils.readList(buffer, new Schema.SchemaConverter()::read));
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		Converter<Schema> schemaConverter = new Schema.SchemaConverter();
		
		//Write all of the schemas into the stream in order.
		NetworkUtils.writeCollection(buffer, schemaMapping.values(), (buf, allocation) -> {
			schemaConverter.write(buffer, allocation.schema);
		});
	}

	@Override
	public int size() {
		return networkSizeData;
	}
}
