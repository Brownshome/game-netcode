package brownshome.netcode;

import java.util.Arrays;

import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotation.converter.Networkable;

/** 
 * This is a small struct that represents the size of a networkable object. It encapsulates the exactness, and constantness, and the size of the object.
 * The object may be considered constant but this is not the case if the object's type changes.
 *
 * @param constant if the size of this object is fixed
 * @param exact if the size of this object is exactly correct
 * @param size the size of the object in bytes
 **/
public record NetworkObjectSize(int size, boolean exact, boolean constant) {
	public static final NetworkObjectSize IDENTITY = new NetworkObjectSize(0, true, true);
	
	public NetworkObjectSize(Networkable networkable) {
		this(networkable.size(), networkable.isSizeExact(), networkable.isSizeConstant());
	}
	
	public <T> NetworkObjectSize(Converter<T> converter, T object) {
		this(converter.size(object), converter.isSizeExact(object), converter.isSizeConstant());
	}
	
	public static NetworkObjectSize combine(NetworkObjectSize a, NetworkObjectSize b) {
		return new NetworkObjectSize(a.size() + b.size(), a.exact && b.exact, a.constant && b.constant);
	}
	
	public static NetworkObjectSize combine(Iterable<NetworkObjectSize> objects) {
		boolean exact = true, constant = true;
		int size = 0;
		
		for (NetworkObjectSize s : objects) {
			exact &= s.exact;
			constant &= s.constant;
			size += s.size;
		}
		
		return new NetworkObjectSize(size, exact, constant);
	}
	
	public static NetworkObjectSize combine(NetworkObjectSize... objects) {
		return combine(Arrays.asList(objects));
	}
	
	public NetworkObjectSize nonConstant() {
		return new NetworkObjectSize(size, exact, false);
	}

	@Override
	public String toString() {
		return Integer.toString(size);
	}
}
