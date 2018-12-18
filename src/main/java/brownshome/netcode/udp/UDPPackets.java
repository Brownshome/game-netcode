package brownshome.netcode.udp;

import brownshome.netcode.Connection;
import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotation.converter.UseConverter;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/**
 * This class contains packets that are sent directly over the UDP connection. They should not be sent by clients
 */
public class UDPPackets {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");

	/** This is a special converter that reads the remaining data from the packet into a buffer, it must only be used for
	 * raw data access, and only for the UDP base packets. */
	static class TrailingByteBufferConverter implements Converter<ByteBuffer> {
		/**
		 * Writes the supplied object into the byte buffer.
		 *
		 * @param buffer
		 * @param object
		 */
		@Override
		public void write(ByteBuffer buffer, ByteBuffer object) {
			buffer.put(object);
		}

		/**
		 * Reads the supplied object from the byte buffer.
		 *
		 * @param buffer
		 */
		@Override
		public ByteBuffer read(ByteBuffer buffer) {
			byte[] array = new byte[buffer.remaining()];
			buffer.get(array);
			return ByteBuffer.wrap(array);
		}

		/**
		 * Returns the size of the object in bytes
		 *
		 * @param object
		 */
		@Override
		public int size(ByteBuffer object) {
			return object.remaining();
		}

		/**
		 * Returns true if the size returned is exactly how many bytes will be needed.
		 *
		 * @param object
		 */
		@Override
		public boolean isSizeExact(ByteBuffer object) {
			return true;
		}

		/**
		 * Returns true if the size returned is always the same number.
		 */
		@Override
		public boolean isSizeConstant() {
			return false;
		}
	}

	/**
	 * This method represents the connect packet on the UDP layer that is sent before the connection is set up.
	 */
	//Client to Server
	@DefinePacket(name = "Connect")
	public static void connect(@ConnectionParam Connection<?> connection, long clientSalt, @UseConverter(Padding.class) Void unused) {
		var udpConnection = (UDPConnection) connection;
		udpConnection.receiveConnectPacket(clientSalt);
	}

	//Server to Client
	//Hash should equal hash(clientSalt)
	@DefinePacket(name = "ConnectionDenied")
	public static void connectionDenied(@ConnectionParam Connection<?> connection, int hash) {
		var udpConnection = (UDPConnection) connection;

		long salt = udpConnection.localSalt();

		CRC32 crc = new CRC32();
		update(crc, salt);
		int digest = (int) crc.getValue();

		if(hash != digest) {
			//Ignore the packet, this is corrupt, or malicious
			LOGGER.info("Corrupt packet received from '" + connection.address() + "'");
		} else {
			udpConnection.connectionDenied();
		}
	}

	/**
	 * This packet sets the challenge salt for the connection
	 * @param hash is hash(clientSalt + serverSalt)
	 */
	@DefinePacket(name = "Challenge")
	public static void challenge(@ConnectionParam Connection<?> connection, int hash, long serverSalt) {
		var udpConnection = (UDPConnection) connection;

		long localSalt = udpConnection.localSalt();

		CRC32 crc = new CRC32();
		update(crc, localSalt);
		update(crc, serverSalt);

		int digest = (int) crc.getValue();

		if(hash != digest) {
			//Ignore the packet, this is corrupt, or malicious
			LOGGER.info("Corrupt packet received from '" + connection.address() + "'");
		} else {
			udpConnection.receiveChallengeSalt(serverSalt);
		}
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
	 * This method represents the data packet type that will be sent across a UDP connection.
	 *
	 * Each packet is made up of a hash, a sequence number, an integer that says what packets have been acked, and the packet
	 * content.
	 *
	 * Each packet content will contain X messages, where each message is a full packet.
	 */
	@DefinePacket(name = "UDPData")
	public static void udpData(@ConnectionParam Connection<?> connection, int hash, int acks, int sequenceNumber, @UseConverter(TrailingByteBufferConverter.class) ByteBuffer messages) {
		var udpConnection = (UDPConnection) connection;

		long localSalt = udpConnection.localSalt();

		CRC32 crc = new CRC32();
		update(crc, localSalt);
		update(crc, acks);
		update(crc, sequenceNumber);
		crc.update(messages.duplicate());
		int digest = (int) crc.getValue();

		if(hash != digest) {
			//Ignore the packet, this is corrupt, or malicious
			LOGGER.info("Corrupt packet received from '" + connection.address() + "'");
		} else {
			udpConnection.receiveBlockOfMessages(messages);
		}
	}
}
