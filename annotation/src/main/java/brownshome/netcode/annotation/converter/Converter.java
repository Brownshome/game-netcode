package brownshome.netcode.annotation.converter;

import java.nio.ByteBuffer;

public interface Converter<T> {
	/** Writes the supplied object into the byte buffer. */
	void write(ByteBuffer buffer, T object);
	
	/** Reads the supplied object from the byte buffer. */
	T read(ByteBuffer buffer);
	
	/** Returns the size of the object in bytes */
	int size(T object);
	
	/** Returns true if the size returned is exactly how many bytes will be needed. */
	boolean isSizeExact(T object);
	
	/** Returns true if the size returned is always the same number. */
	boolean isSizeConstant();
}
