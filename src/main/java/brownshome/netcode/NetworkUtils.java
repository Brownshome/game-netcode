package brownshome.netcode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import brownshome.netcode.sizing.NetworkObjectSize;

public final class NetworkUtils {
	public static final NetworkObjectSize INT_SIZE = new NetworkObjectSize(Integer.BYTES, true, true);
	public static final NetworkObjectSize LONG_SIZE = new NetworkObjectSize(Long.BYTES, true, true);
	
	/** This constant of 3 occurs when the char 0xFFFF is encoded. */
	private static final int MAXIMUM_UTF8_BYTES_PER_CHAR = 3;
	
	private NetworkUtils() {  }

	/** Reads a length prefixed UTF-8 string from the buffer. */
	public static String readString(ByteBuffer buffer) {
		int length = buffer.getInt();
		
		if(buffer.hasArray()) {
			String string = new String(buffer.array(), buffer.position(), length, StandardCharsets.UTF_8);
			buffer.position(buffer.position() + length);
			return string;
		}
		
		//Guard against OOM
		if(length > buffer.remaining()) {
			throw new IllegalArgumentException("Not enough data to build a string of length " + length);
		}
		
		byte[] array = new byte[length];
		
		buffer.get(array);
		
		return new String(array, StandardCharsets.UTF_8);
	}

	/** Reads a length prefixed list, the list will be modifiable. The function must read at least one byte from the buffer for each list item. */
	public static <T> List<T> readList(ByteBuffer buffer, Function<? super ByteBuffer, ? extends T> itemFunc) {
		int length = buffer.getInt();
		
		if(length > buffer.remaining()) {
			throw new IllegalArgumentException("Not enough data to build a list of length " + length);
		}
		
		List<T> list = new ArrayList<>(length);
		
		for(int i = 0; i < length; i++) {
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
		
		for(T t : items) {
			itemFunc.accept(buffer, t);
		}
	}
	
	/**
	 * Calculates the length of a stored String including the header
	 */
	public static NetworkObjectSize calculateSize(String s) {
		return new NetworkObjectSize(s.length() * MAXIMUM_UTF8_BYTES_PER_CHAR + Integer.BYTES, false, false);
	}
	
	/**
	 * Calculates the length of a stored list including the header
	 */
	public static <T> NetworkObjectSize calculateSize(Collection<T> collection, Function<? super T, NetworkObjectSize> converter) {
		NetworkObjectSize rawList = collection.stream()
				.map(converter::apply)
				.reduce(NetworkObjectSize.IDENTITY, NetworkObjectSize::combine);
		
		return NetworkObjectSize.combine(INT_SIZE, rawList).nonConstant();
	}
}
