package brownshome.netcode.packets;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;

import javax.annotation.processing.Generated;

import brownshome.netcode.Packet;
import brownshome.netcode.Schema;

@Generated("brownshome.netcode.annotationprocessor.NetworkSchemaGenerator")
public final class BaseSchema extends Schema {
	/** The currently supported version. */
	public static final int MAJOR_VERSION = 0, MINOR_VERSION = 0;
	
	/** The name of this schema */
	public static final String FULL_NAME = "brownshome.netcode.packets.Base", SHORT_NAME = "Base";
	
	public BaseSchema() {
		this(MAJOR_VERSION, MINOR_VERSION);
	}
	
	public BaseSchema(int majorVersion, int minorVersion) {
		super(SHORT_NAME, FULL_NAME, majorVersion, minorVersion, createPacketList(minorVersion));
		
		assert minorVersion <= MINOR_VERSION : "Minor version not supported";
	}

	private static List<Function<ByteBuffer, Packet>> createPacketList(int minorVersion) {
		switch(minorVersion) {
		case 0:
			return List.of(HelloPacket::new, NegotiateProtocolPacket::new, ConfirmProtocolPacket::new, NegotiationFailedPacket::new);
		default:
			throw new IllegalArgumentException(String.format("Invalid minor version number, valid numbers are [0, %d]", MINOR_VERSION));
		}
	}

	@Override
	public Schema withMinorVersion(int minorVersion) {
		return new BaseSchema(majorVersion(), minorVersion);
	}
}