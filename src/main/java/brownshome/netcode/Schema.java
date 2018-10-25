package brownshome.netcode;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;

import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.sizing.NetworkObjectSize;

/**
 * Represents a versioned collection of packets that share a similar purpose. This class handles
 * the mapping of IDs to packets.
 * 
 * @author James Brown
 */
public abstract class Schema {
	public static final class SchemaConverter implements Converter<Schema> {
		@Override
		public void write(ByteBuffer buffer, Schema schema) {
			buffer.putInt(schema.majorVersion()).putInt(schema.minorVersion());
			
			NetworkUtils.writeString(buffer, schema.fullName());
		}

		@Override
		public Schema read(ByteBuffer buffer) {
			int major, minor;
			String fullName;
			
			major = buffer.getInt();
			minor = buffer.getInt();
			
			fullName = NetworkUtils.readString(buffer);
			
			//Load the schema class
			try {
				Class<?> schemaClass = Class.forName(String.format("%sSchema", fullName));
				return (Schema) schemaClass.getDeclaredConstructor(Integer.TYPE, Integer.TYPE).newInstance(major, minor);
			} catch(ClassCastException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
				throw new IllegalArgumentException(String.format("Unable to create a schema for %s", fullName), e);
			}
		}

		@Override
		public int size(Schema schema) {
			return schema.size.size();
		}

		@Override
		public boolean isSizeExact(Schema schema) {
			return schema.size.isExact();
		}

		@Override
		public boolean isSizeConstant() {
			return false;
		}
	}
	
	private final int major, minor;
	private final String fullName, shortName;
	private final List<Function<ByteBuffer, Packet>> packetConstructors;
	
	private final NetworkObjectSize size;
	
	protected Schema(String shortName, String fullName, 
			int majorVersion, int minorVersion, 
			List<Function<ByteBuffer, Packet>> packetConstructors) {
		
		this.minor = minorVersion;
		this.major = majorVersion;
		this.fullName = fullName;
		this.shortName = shortName;
		this.packetConstructors = packetConstructors;
		
		size = NetworkObjectSize.combine(
				NetworkUtils.INT_SIZE,
				NetworkUtils.INT_SIZE,
				NetworkUtils.calculateSize(fullName));
	}
	
	public final Packet createPacket(int packetId, ByteBuffer data) {
		Function<ByteBuffer, Packet> constructor;
		
		try {
			constructor = packetConstructors.get(packetId);
		} catch(IndexOutOfBoundsException ioobe) {
			throw new IllegalArgumentException("Invalid packet index: " + packetId, ioobe);
		}
		
		if(constructor == null) {
			throw new IllegalArgumentException("Invalid packet index: " + packetId);
		}
		
		return constructor.apply(data);
	}
	
	public final String shortName() { return shortName; }
	public final String fullName() { return fullName; }
	
	public final int minorVersion() { return minor; }
	public final int majorVersion() { return major; }

	public final int numberOfIDsRequired() {
		return packetConstructors.size();
	}

	public abstract Schema withMinorVersion(int minorVersion);
}
