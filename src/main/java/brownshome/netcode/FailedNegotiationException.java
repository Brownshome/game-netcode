package brownshome.netcode;

public class FailedNegotiationException extends Exception {
	public FailedNegotiationException(String reason) {
		super(reason);
	}
}
