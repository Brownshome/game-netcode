package brownshome.netcode.udp;

import brownshome.netcode.annotation.converter.Converter;

import java.nio.ByteBuffer;

public class Padding implements Converter<Void> {
	public static final int PADDING = 200;
	public static final byte[] PADDING_DATA = new byte[PADDING];

	/**
	 * Writes the supplied object into the byte buffer.
	 *
	 * @param buffer
	 * @param object
	 */
	@Override
	public void write(ByteBuffer buffer, Void object) {
		//put all zeros to avoid sending data
		buffer.put(PADDING_DATA);
	}

	/**
	 * Reads the supplied object from the byte buffer.
	 *
	 * @param buffer
	 */
	@Override
	public Void read(ByteBuffer buffer) {
		//discard the data
		buffer.position(buffer.position() + PADDING);

		return null;
	}

	/**
	 * Returns the size of the object in bytes
	 *
	 * @param object
	 */
	@Override
	public int size(Void object) {
		return PADDING;
	}

	/**
	 * Returns true if the size returned is exactly how many bytes will be needed.
	 *
	 * @param object
	 */
	@Override
	public boolean isSizeExact(Void object) {
		return true;
	}

	/**
	 * Returns true if the size returned is always the same number.
	 */
	@Override
	public boolean isSizeConstant() {
		return true;
	}
}
