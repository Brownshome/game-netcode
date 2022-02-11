package brownshome.netcode.annotation.converter;

import java.nio.ByteBuffer;

/** Implementers of this class must define a public constructor that takes a byte buffer. */
public interface Networkable {
	void write(ByteBuffer buffer);
	
	/** Returns the maximum size of this object in bytes */
	int size();
}