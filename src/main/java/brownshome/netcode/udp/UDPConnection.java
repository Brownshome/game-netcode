package brownshome.netcode.udp;

import brownshome.netcode.NetworkConnection;
import brownshome.netcode.Packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class UDPConnection extends NetworkConnection<InetSocketAddress> {
	private static final Logger LOGGER = Logger.getLogger("brownshome.netcode");
	private final UDPConnectionManager manager;

	public UDPConnection(UDPConnectionManager manager, InetSocketAddress other) {
		super(other);

		this.manager = manager;
	}

	@Override
	public CompletableFuture<Void> sendWithoutStateChecks(Packet packet) {
		ByteBuffer buffer = ByteBuffer.allocate(packet.size() + Integer.BYTES);
		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
		buffer.flip();

		try {
			manager.channel().send(buffer, address());
			return CompletableFuture.completedFuture(null);
		} catch(IOException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public UDPConnectionManager connectionManager() {
		return manager;
	}

	protected void receive(ByteBuffer buffer) {
		Packet incoming = protocol().createPacket(buffer);

		LOGGER.info(() -> String.format("Address '%s' received '%s'", address(), incoming.toString()));

		manager.executeOn(() -> {
			protocol().handle(this, incoming);
		}, incoming.handledBy());
	}
}
