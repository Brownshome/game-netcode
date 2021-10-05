package brownshome.netcode.ordering;

public class InvalidSequenceNumberException extends IllegalArgumentException {
	public InvalidSequenceNumberException() { }

	public InvalidSequenceNumberException(String s) {
		super(s);
	}
}
