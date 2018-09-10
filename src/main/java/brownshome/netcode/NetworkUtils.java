package brownshome.netcode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class NetworkUtils {
	public static final Logger LOGGER = Logger.getLogger("brownshome.netcode");
	
	private NetworkUtils() {  }

	/** Reads a length prefixed UTF-8 string from the buffer. */
	public static String readString(ByteBuffer buffer) {
		int length = buffer.getInt();
		
		if(buffer.hasArray()) {
			return new String(buffer.array(), buffer.position(), length, StandardCharsets.UTF_8);
		}
		
		//Guard against OOM
		if(length > buffer.remaining()) {
			throw new IllegalArgumentException("Not enough data to build a string of length " + length);
		}
		
		byte[] array = new byte[length];
		
		buffer.get(array);
		
		return new String(array, StandardCharsets.UTF_8);
	}

	/** Reads a length prefixed list, the list will be modifiable. */
	public static <T> List<T> readList(ByteBuffer buffer, Function<ByteBuffer, T> itemFunc) {
		int length = buffer.getInt();
		
		List<T> list = new ArrayList<>(length);
		
		for(int i = 0; i < list.size(); i++) {
			list.add(itemFunc.apply(buffer));
		}
		
		return list;
	}

	public static void writeString(ByteBuffer buffer, String string) {
		byte[] array = string.getBytes(StandardCharsets.UTF_8);
		
		buffer.putInt(array.length);
		buffer.put(array);
	}

	public static <T> void writeCollection(ByteBuffer buffer, Collection<T> items, BiConsumer<ByteBuffer, T> itemFunc) {
		buffer.putInt(items.size());
		
		for(T t : items) {
			itemFunc.accept(buffer, t);
		}
	}
	
	/**
	 * Calculates the length of a stored String not include the header. This method allocates the string array, and
	 * so is not particularly fast.
	 */
	public static int calculateLength(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}
}
