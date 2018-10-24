package brownshome.netcode.packets;

import java.nio.ByteBuffer;
import java.util.List;

import brownshome.netcode.Connection;
import brownshome.netcode.NetworkException;
import brownshome.netcode.NetworkUtils;
import brownshome.netcode.Packet;
import brownshome.netcode.Schema;
import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.sizing.NetworkObjectSize;

public final class NegotiateProtocolPacket extends Packet {
	private final List<Schema> schemaData;
	private final Converter<Schema> schemaConverter;
	
	private final NetworkObjectSize size;
	
	public NegotiateProtocolPacket(List<Schema> schemaData) {
		super(BaseSchema.FULL_NAME, "default", 1);

		this.schemaData = schemaData;
		this.schemaConverter = new Schema.SchemaConverter();
		
		NetworkObjectSize schemaSize = NetworkUtils.calculateSize(schemaConverter, schemaData);
		
		size = NetworkObjectSize.combine(schemaSize);
	}

	protected NegotiateProtocolPacket(ByteBuffer buffer) {
		this(NetworkUtils.readList(buffer, new Schema.SchemaConverter()::read));
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		NetworkUtils.writeCollection(buffer, schemaData, schemaConverter::write);
	}

	@Override
	public int size() {
		return size.size();
	}

	@Override
	public boolean isSizeExact() {
		return size.isExact();
	}
	
	@Override
	public boolean isSizeConstant() {
		return size.isConstant();
	}
	
	@Override
	public void handle(Connection<?> connection, int minorVersion) throws NetworkException {
		BasePackets.sendProtocolBack(connection, schemaData);
	}
}
