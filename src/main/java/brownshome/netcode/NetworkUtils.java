package brownshome.netcode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class NetworkUtils {
	public static final int BYTE_SIZE = Byte.BYTES;
	public static final int SHORT_SIZE = Short.BYTES;
	public static final int FLOAT_SIZE = Float.BYTES;
	public static final int INT_SIZE = Integer.BYTES;
	public static final int LONG_SIZE = Long.BYTES;
	public static final int DOUBLE_SIZE = Double.BYTES;

	/** This constant of 3 occurs when the char 0xFFFF is encoded. */
	private static final int MAXIMUM_UTF8_BYTES_PER_CHAR = 3;
	
	private NetworkUtils() {  }

	/** Reads a length prefixed UTF-8 string from the buffer. */
	public static String readString(ByteBuffer buffer) {
		int length = buffer.getInt();
		
		if (buffer.hasArray()) {
			String string = new String(buffer.array(), buffer.position(), length, StandardCharsets.UTF_8);
			buffer.position(buffer.position() + length);
			return string;
		}
		
		//Guard against OOM
		if (length > buffer.remaining()) {
			throw new IllegalArgumentException("Not enough data to build a string of length " + length);
		}
		
		byte[] array = new byte[length];
		
		buffer.get(array);
		
		return new String(array, StandardCharsets.UTF_8);
	}

	/** Reads a length prefixed list, the list will be modifiable. The function must read at least one byte from the buffer for each list item. */
	public static <T> List<T> readList(ByteBuffer buffer, Function<? super ByteBuffer, ? extends T> itemFunc) {
		int length = buffer.getInt();
		
		if (length > buffer.remaining()) {
			throw new IllegalArgumentException("Not enough data to build a list of length " + length);
		}
		
		List<T> list = new ArrayList<>(length);
		
		for (int i = 0; i < length; i++) {
			list.add(itemFunc.apply(buffer));
		}
		
		return list;
	}

	public static void writeString(ByteBuffer buffer, String string) {
		byte[] array = string.getBytes(StandardCharsets.UTF_8);
		
		buffer.putInt(array.length);
		buffer.put(array);
	}

	public static <T> void writeCollection(ByteBuffer buffer, Collection<T> items, BiConsumer<? super ByteBuffer, ? super T> itemFunc) {
		buffer.putInt(items.size());
		
		for (T t : items) {
			itemFunc.accept(buffer, t);
		}
	}
	
	/**
	 * Calculates the length of a stored String including the header
	 */
	public static int calculateSize(String s) {
		return s.length() * MAXIMUM_UTF8_BYTES_PER_CHAR + Integer.BYTES;
	}
	
	/**
	 * Calculates the length of a stored list including the header
	 */
	public static <T> int calculateSize(Collection<T> collection, ToIntFunction<? super T> converter) {
		var rawSize = collection.stream()
				.mapToInt(converter)
				.sum();
		
		return rawSize + Integer.BYTES;
	}
}
