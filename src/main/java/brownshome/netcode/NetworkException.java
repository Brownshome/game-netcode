package brownshome.netcode;

public class NetworkException extends RuntimeException {
	public NetworkException(String message, Connection<?> connection) {
		super(String.format("Error communicating with '%s': %s", connection.address(), message));
	}

	public NetworkException(Throwable cause, Connection<?> connection) {
		super(String.format("Error communicating with '%s'", connection.address()), cause);
	}
}
