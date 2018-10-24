package brownshome.netcode.sizing;

import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotation.converter.Networkable;

/** 
 * This is a small struct that represents the size of a networkable object. It encapsulates the exactness, and constantness, and the size of the object.
 * The object may be considered constant but this is not the case if the object's type changes.
 **/
public final class NetworkObjectSize {
	public static final NetworkObjectSize IDENTITY = new NetworkObjectSize(0, true, true);
	
	private final int size;
	private final boolean exact, constant;
	
	public NetworkObjectSize(int size, boolean exact, boolean constant) {
		this.size = size;
		this.exact = exact;
		this.constant = constant;
	}
	
	public NetworkObjectSize(Networkable networkable) {
		this(networkable.size(), networkable.isSizeExact(), networkable.isSizeConstant());
	}
	
	public <T> NetworkObjectSize(Converter<T> converter, T object) {
		this(converter.size(object), converter.isSizeExact(object), converter.isSizeConstant());
	}
	
	public static NetworkObjectSize combine(NetworkObjectSize a, NetworkObjectSize b) {
		return new NetworkObjectSize(a.size() + b.size(), a.isExact() && b.isExact(), a.isConstant() && b.isConstant());
	}
	
	public static NetworkObjectSize combine(Iterable<NetworkObjectSize> objects) {
		boolean exact = true, constant = true;
		int size = 0;
		
		for(NetworkObjectSize s : objects) {
			exact &= s.exact;
			constant &= s.constant;
			size += s.size;
		}
		
		return new NetworkObjectSize(size, exact, constant);
	}
	
	public static NetworkObjectSize combine(NetworkObjectSize... objects) {
		boolean exact = true, constant = true;
		int size = 0;
		
		for(NetworkObjectSize s : objects) {
			exact &= s.exact;
			constant &= s.constant;
			size += s.size;
		}
		
		return new NetworkObjectSize(size, exact, constant);
	}
	
	/** The size of the object in bytes */
	public int size() {
		return size;
	}
	
	public boolean isExact() {
		return exact;
	}
	
	public boolean isConstant() {
		return constant;
	}

	public NetworkObjectSize nonConstant() {
		return new NetworkObjectSize(size, exact, false);
	}
}
