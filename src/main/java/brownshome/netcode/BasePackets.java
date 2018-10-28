package brownshome.netcode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.converter.UseConverter;

/** This class contains all of the packets used by the base protocol. */
public final class BasePackets {
	private static final Logger LOGGER = Logger.getLogger("Netcode");
	
	private BasePackets() {  }
	
	@DefinePacket(name = "Hello")
	public static void sayHelloBack(@ConnectionParam Connection<?> connection, int numberOfWaves) {
		System.out.println(String.format("Hello! (%d left)", numberOfWaves));
		
		if(numberOfWaves != 0)
			connection.send(new HelloPacket(numberOfWaves - 1));
	}
	
	@DefinePacket(name = "NegotiateProtocol")
	public static void sendProtocolBack(@ConnectionParam Connection<?> connection, @UseConverter(Schema.SchemaConverter.class) List<Schema> schemas) {
		Map<String, Schema> supportedSchemas = new HashMap<>();
		
		for(Schema s : connection.connectionManager().schemas()) {
			supportedSchemas.put(s.fullName(), s);
		}
		
		List<Schema> chosenSchema = new ArrayList<>();
		
		for(Schema s : schemas) {
			//If the schema does not exist, or the major versions don't match, fail the connection.
			
			Schema supported = supportedSchemas.get(s.fullName());
			
			if(supported == null || supported.majorVersion() != s.majorVersion()) {
				connection.send(new NegotiationFailedPacket(String.format("Unsupported schema: %s v%d", s.shortName(), s.majorVersion())));
				//Keep trying the connection, the client can work out if the failed schema were important.
			} else {
				int minorVersion = Math.min(s.minorVersion(), supported.minorVersion());
				
				chosenSchema.add(s.withMinorVersion(minorVersion));
			}
		}
		
		Protocol protocol = new Protocol(chosenSchema);
		
		connection.send(new ConfirmProtocolPacket(protocol));
		
		connection.receiveNegotiatePacket(protocol);
	}
	
	@DefinePacket(name = "NegotiationFailed")
	public static void negotiationFailed(@ConnectionParam Connection<?> connection, String reason) {
		LOGGER.severe(String.format("Error negotiating schema with '%s': %s", connection.address(), reason));
	}
	
	@DefinePacket(name = "ConfirmProtocol")
	public static void confirmProtocol(@ConnectionParam Connection<?> connection, Protocol protocol) {
		connection.receiveConfirmPacket(protocol);
	}

	@DefinePacket(name = "CloseConnection")
	public static void closeConnection(@ConnectionParam Connection<?> connection) {
		connection.closeConnection(false);
	}
}
