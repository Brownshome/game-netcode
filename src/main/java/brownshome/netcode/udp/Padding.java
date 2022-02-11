package brownshome.netcode.udp;

import brownshome.netcode.annotation.converter.Converter;

import java.nio.ByteBuffer;

final class Padding implements Converter<Void> {
	public static final int PADDING = 200;
	public static final byte[] PADDING_DATA = new byte[PADDING];

	@Override
	public void write(ByteBuffer buffer, Void object) {
		//put all zeros to avoid sending data
		buffer.put(PADDING_DATA);
	}

	@Override
	public Void read(ByteBuffer buffer) {
		//discard the data
		buffer.position(buffer.position() + PADDING);

		return null;
	}

	@Override
	public int size(Void object) {
		return PADDING;
	}
}
