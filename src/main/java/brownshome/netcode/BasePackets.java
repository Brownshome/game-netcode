package brownshome.netcode;

import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.MakeReliable;
import brownshome.netcode.annotation.converter.UseConverter;

import java.util.List;
import java.util.logging.Logger;

/** This class contains all the packets used by the base protocol. */
final class BasePackets {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");
	
	private BasePackets() {  }
	
	@DefinePacket(name = "NegotiateProtocol")
	@MakeReliable
	static void sendProtocolBack(@ConnectionParam Connection<?> connection, @UseConverter(Schema.SchemaConverter.class) List<Schema> schemas) {
		assert connection instanceof NetworkConnection;

		Protocol.ProtocolNegotiation negotiationResult = Protocol.negotiateProtocol(schemas, connection.connectionManager().schemas());
		
		connection.send(new ConfirmProtocolPacket(negotiationResult.protocol()));

		((NetworkConnection<?>) connection).receiveNegotiatePacket(negotiationResult.protocol());
	}
	
	@DefinePacket(name = "NegotiationFailed")
	@MakeReliable
	static void negotiationFailed(@ConnectionParam Connection<?> connection, String reason) {
		LOGGER.severe(String.format("Error negotiating schema with '%s': %s", connection.address(), reason));
	}
	
	@DefinePacket(name = "ConfirmProtocol")
	@MakeReliable
	static void confirmProtocol(@ConnectionParam Connection<?> connection, Protocol protocol) {
		assert connection instanceof NetworkConnection;

		((NetworkConnection<?>) connection).receiveConfirmPacket(protocol);
	}

	@DefinePacket(name = "CloseConnection")
	@MakeReliable
	static void closeConnection(@ConnectionParam Connection<?> connection) {
		assert connection instanceof NetworkConnection;

		((NetworkConnection<?>) connection).receiveClosePacket();
	}

	@DefinePacket(name = "Error")
	@MakeReliable
	static void error(@ConnectionParam Connection<?> connection, String message) {
		LOGGER.severe(String.format("Unexpected error handling packet for '%s': %s", connection.address(), message));
	}
}
