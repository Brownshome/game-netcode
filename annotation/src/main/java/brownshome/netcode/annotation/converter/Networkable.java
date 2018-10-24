package brownshome.netcode.annotation.converter;

import java.nio.ByteBuffer;

/** Implementers of this class must define a public constructor that takes a byte buffer. */
public interface Networkable {
	void write(ByteBuffer buffer);
	
	/** Returns the size of the object in bytes */
	int size();
	
	/** Returns true if the size returned is exactly how many bytes will be needed. */
	boolean isSizeExact();
	
	/** Returns true if the size returned is always the same number. */
	boolean isSizeConstant();
}