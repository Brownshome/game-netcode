package brownshome.netcode.ordering;

public class InvalidSequenceNumberException extends IllegalArgumentException {
	/**
	 * Constructs an <code>InvalidSequenceNumberException</code> with no
	 * detail message.
	 */
	public InvalidSequenceNumberException() {
	}

	/**
	 * Constructs an <code>InvalidSequenceNumberException</code> with the
	 * specified detail message.
	 *
	 * @param s the detail message.
	 */
	public InvalidSequenceNumberException(String s) {
		super(s);
	}
}
