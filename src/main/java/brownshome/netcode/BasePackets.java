package brownshome.netcode;

import java.util.List;

import brownshome.netcode.annotation.*;
import brownshome.netcode.annotation.converter.UseConverter;

/** This class contains all the packets used by the base protocol. */
final class BasePackets {
	private static final System.Logger LOGGER = System.getLogger(BasePackets.class.getModule().getName());
	
	private BasePackets() {  }
	
	@DefinePacket(name = "NegotiateProtocol")
	@MakeReliable
	static void sendProtocolBack(@ConnectionParam Connection<?> connection, @UseConverter(Schema.SchemaConverter.class) List<Schema> schemas) {
		Protocol.ProtocolNegotiation negotiationResult = Protocol.negotiateProtocol(schemas, connection.connectionManager().schemas());
		((NetworkConnection<?>) connection).receiveNegotiatePacket(negotiationResult);
	}
	
	@DefinePacket(name = "NegotiationFailed")
	@MakeReliable
	static void negotiationFailed(@ConnectionParam Connection<?> connection, String reason) {
		((NetworkConnection<?>) connection).receiveNegotiationFailedPacket(reason);
	}
	
	@DefinePacket(name = "ConfirmProtocol")
	@MakeReliable
	static void confirmProtocol(@ConnectionParam Connection<?> connection, Protocol protocol) {
		((NetworkConnection<?>) connection).receiveConfirmPacket(protocol);
	}

	@DefinePacket(name = "CloseConnection")
	@MakeReliable
	static void closeConnection(@ConnectionParam Connection<?> connection) {
		((NetworkConnection<?>) connection).receiveClosePacket();
	}

	@DefinePacket(name = "Error")
	@MakeReliable
	static void error(@ConnectionParam Connection<?> connection, String message) {
		LOGGER.log(System.Logger.Level.ERROR, "Unexpected error handling packet for ''{0}'': {1}", connection.address(), message);
	}
}
