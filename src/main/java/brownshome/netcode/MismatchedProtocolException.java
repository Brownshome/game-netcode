package brownshome.netcode;

public class MismatchedProtocolException extends NetworkException {
	public MismatchedProtocolException(Schema schema, Connection<?> connection) {
		super(String.format("Schema '%s' is not supported by this connection", schema), connection);
	}
	
	public MismatchedProtocolException(Packet packet, Connection<?> connection) {
		super(String.format("Packet '%s' is not supported by this connection", packet), connection);
	}
}
