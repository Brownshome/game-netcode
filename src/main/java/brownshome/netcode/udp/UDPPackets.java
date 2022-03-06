package brownshome.netcode.udp;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import brownshome.netcode.Connection;
import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotation.converter.UseConverter;

/**
 * This class contains packets that are sent directly over the UDP connection. They should not be sent by clients
 */
final class UDPPackets {
	private static final System.Logger LOGGER = System.getLogger(UDPPackets.class.getModule().getName());

	private UDPPackets() { }

	/** This is a special converter that reads the remaining data from the packet into a buffer, it must only be used for
	 * raw data access, and only for the UDP base packets. */
	static class TrailingByteBufferConverter implements Converter<ByteBuffer> {
		@Override
		public void write(ByteBuffer buffer, ByteBuffer object) {
			buffer.put(object);
		}

		@Override
		public ByteBuffer read(ByteBuffer buffer) {
			byte[] array = new byte[buffer.remaining()];
			buffer.get(array);
			return ByteBuffer.wrap(array);
		}

		@Override
		public int size(ByteBuffer object) {
			return object.remaining();
		}
	}

	/**
	 * This method represents the connect-packet on the UDP layer that is sent before the connection is set up.
	 */
	// Client to Server
	@DefinePacket
	public static void connect(@ConnectionParam Connection<?, ?> connection, long clientSalt, @UseConverter(Padding.class) Void unused) {
		UDPConnection udpConnection;

		try {
			udpConnection = (UDPConnection) connection;
		} catch (ClassCastException cce) {
			throw new IllegalStateException("'Connect' can only be received by a UDP connection", cce);
		}

		udpConnection.receiveConnectPacket(clientSalt);
	}

	// Server to Client
	// Hash should equal hash(clientSalt)
	@DefinePacket
	public static void connectionDenied(@ConnectionParam Connection<?, ?> connection, int hash) {
		UDPConnection udpConnection;

		try {
			udpConnection = (UDPConnection) connection;
		} catch (ClassCastException cce) {
			throw new IllegalStateException("'ConnectionDenied' can only be received by a UDP connection", cce);
		}

		int digest = hashConnectionDeniedPacket(udpConnection.localSalt());

		if (hash != digest) {
			// Ignore the packet, this is corrupt, or malicious
			LOGGER.log(System.Logger.Level.INFO, "Corrupt packet received from '" + connection.address() + "'");
		} else {
			udpConnection.connectionDenied();
		}
	}

	/**
	 * This packet sets the challenge salt for the connection
	 * @param hash is hash(clientSalt + serverSalt)
	 */
	@DefinePacket
	public static void challenge(@ConnectionParam Connection<?, ?> connection, int hash, long serverSalt) {
		UDPConnection udpConnection;

		try {
			udpConnection = (UDPConnection) connection;
		} catch (ClassCastException cce) {
			throw new IllegalStateException("'Challenge' can only be received by a UDP connection", cce);
		}

		long localSalt = udpConnection.localSalt();

		int digest = hashChallengePacket(localSalt, serverSalt);

		if (hash != digest) {
			// Ignore the packet, this is corrupt, or malicious
			LOGGER.log(System.Logger.Level.INFO, "Corrupt packet received from '" + connection.address() + "'");
		} else {
			udpConnection.receiveChallengeSalt(serverSalt);
		}
	}

	public static int hashConnectionDeniedPacket(long clientSalt) {
		CRC32 crc = new CRC32();
		update(crc, clientSalt);
		return (int) crc.getValue();
	}

	/**
	 * Creates a hash for a challenge packet, the remote salt is the client salt, and the localSalt is the server salt.
	 */
	public static int hashChallengePacket(long clientSalt, long serverSalt) {
		CRC32 crc = new CRC32();
		update(crc, clientSalt);
		update(crc, serverSalt);

		return (int) crc.getValue();
	}

	/**
	 * Creates a hash for a data packet, the remote salt is the client salt, and the localSalt is the server salt.
	 *
	 * The buffer will be consumer in this process
	 */
	public static int hashDataPacket(long remoteSalt, Acknowledgement acknowledgement,
	                                 int sequenceNumber, int olderRequiredPackets, ByteBuffer messages) {
		CRC32 crc = new CRC32();

		update(crc, remoteSalt);
		update(crc, acknowledgement.oldestAcknowledgement());
		update(crc, acknowledgement.acknowledgement());
		update(crc, sequenceNumber);
		update(crc, olderRequiredPackets);

		crc.update(messages);

		return (int) crc.getValue();
	}

	private static void update(CRC32 crc, long val) {
		crc.update((int) (val));
		crc.update((int) (val << 8));
		crc.update((int) (val << 16));
		crc.update((int) (val << 24));

		crc.update((int) (val << 32));
		crc.update((int) (val << 40));
		crc.update((int) (val << 48));
		crc.update((int) (val << 56));
	}

	private static void update(CRC32 crc, int val) {
		crc.update(val);
		crc.update(val << 8);
		crc.update(val << 16);
		crc.update(val << 24);
	}

	/**
	 * A packet of UDP data
	 * @param connection the connection
	 * @param hash a hash of the local salt and all other fields
	 * @param acknowledgement previous acknowledgements
	 * @param sequenceNumber the sequence number of this packet
	 * @param olderRequiredPackets older sequence numbers that need to be received before this packet can be received
	 * @param messages the data in the packet
	 */
	@DefinePacket
	public static void udpData(@ConnectionParam Connection<?, ?> connection,
	                           int hash,
	                           Acknowledgement acknowledgement,
							   int sequenceNumber, int olderRequiredPackets,
							   @UseConverter(TrailingByteBufferConverter.class) ByteBuffer messages) {

		if (!(connection instanceof UDPConnection udpConnection)) {
			throw  new IllegalStateException("'UDPData' can only be received by a UDP connection");
		}

		long localSalt = udpConnection.localSalt();

		int digest = hashDataPacket(localSalt, acknowledgement, sequenceNumber, olderRequiredPackets, messages.duplicate());

		if (hash != digest) {
			// Ignore the packet, this is corrupt, or malicious
			LOGGER.log(System.Logger.Level.INFO, "Corrupt packet received from '" + connection.address() + "'");
		} else {
			for (int i : acknowledgement) {
				udpConnection.receiveAcknowledgement(i);
			}

			if (!udpConnection.onSequenceNumberReceived(sequenceNumber, messages.hasRemaining())) {
				LOGGER.log(System.Logger.Level.DEBUG, "Rejected duplicate message " + sequenceNumber + " from '" + connection.address() + "'");
				return;
			}

			udpConnection.receiveMessages(sequenceNumber, olderRequiredPackets, messages);
		}
	}
}
