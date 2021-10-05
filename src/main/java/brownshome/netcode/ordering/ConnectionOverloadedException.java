package brownshome.netcode.ordering;

public class ConnectionOverloadedException extends IllegalStateException {
	public ConnectionOverloadedException() { }

	public ConnectionOverloadedException(String s) {
		super(s);
	}
}
