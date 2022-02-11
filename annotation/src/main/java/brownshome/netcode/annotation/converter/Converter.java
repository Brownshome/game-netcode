package brownshome.netcode.annotation.converter;

import java.nio.ByteBuffer;

public interface Converter<T> {
	/** Writes the supplied object into the byte buffer. */
	void write(ByteBuffer buffer, T object);
	
	/** Reads the supplied object from the byte buffer. */
	T read(ByteBuffer buffer);
	
	/** Returns the size of the object in bytes */
	int size(T object);
}
