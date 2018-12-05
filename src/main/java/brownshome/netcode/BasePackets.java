package brownshome.netcode;

import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.converter.UseConverter;

import java.util.List;
import java.util.logging.Logger;

/** This class contains all of the packets used by the base protocol. */
public final class BasePackets {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");
	
	private BasePackets() {  }
	
	@DefinePacket(name = "Hello")
	static void sayHelloBack(@ConnectionParam Connection<?> connection, int numberOfWaves) {
		System.out.println(String.format("Hello! (%d left)", numberOfWaves));
		
		if(numberOfWaves != 0)
			connection.send(new HelloPacket(numberOfWaves - 1));
	}
	
	@DefinePacket(name = "NegotiateProtocol")
	static void sendProtocolBack(@ConnectionParam Connection<?> connection, @UseConverter(Schema.SchemaConverter.class) List<Schema> schemas) {
		assert connection instanceof NetworkConnection;

		Protocol.ProtocolNegotiation negotiationResult = Protocol.negotiateProtocol(schemas, connection.connectionManager().schemas());
		
		connection.send(new ConfirmProtocolPacket(negotiationResult.protocol));

		((NetworkConnection) connection).receiveNegotiatePacket(negotiationResult.protocol);
	}
	
	@DefinePacket(name = "NegotiationFailed")
	static void negotiationFailed(@ConnectionParam Connection<?> connection, String reason) {
		LOGGER.severe(String.format("Error negotiating schema with '%s': %s", connection.address(), reason));
	}
	
	@DefinePacket(name = "ConfirmProtocol")
	static void confirmProtocol(@ConnectionParam Connection<?> connection, Protocol protocol) {
		assert connection instanceof NetworkConnection;

		((NetworkConnection) connection).receiveConfirmPacket(protocol);
	}

	@DefinePacket(name = "CloseConnection")
	static void closeConnection(@ConnectionParam Connection<?> connection) {
		assert connection instanceof NetworkConnection;

		((NetworkConnection) connection).receiveClosePacket();
	}

	@DefinePacket(name = "Error")
	static void error(@ConnectionParam Connection<?> connection, String message) {
		LOGGER.severe(String.format("Unexpected error handling packet for '%s': %s", connection.address(), message));
	}
}
