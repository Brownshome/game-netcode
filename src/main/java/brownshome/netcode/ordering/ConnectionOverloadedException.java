package brownshome.netcode.ordering;

public class ConnectionOverloadedException extends IllegalStateException {
	/**
	 * Constructs an ConnectionOverloadedException with no detail message.
	 * A detail message is a String that describes this particular exception.
	 */
	public ConnectionOverloadedException() {
	}

	/**
	 * Constructs an ConnectionOverloadedException with the specified detail
	 * message.  A detail message is a String that describes this particular
	 * exception.
	 *
	 * @param s the String that contains a detailed message
	 */
	public ConnectionOverloadedException(String s) {
		super(s);
	}
}
