package brownshome.netcode.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import brownshome.netcode.Connection;
import brownshome.netcode.Packet;

public class MemoryConnection extends Connection<MemoryConnectionManager> {
	private final MemoryConnectionManager other;
	private final MemoryConnectionManager manager;
	
	protected MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		super(other);

		this.other = other;
		this.manager = manager;
	}

	@Override
	protected CompletableFuture<Void> sendWithoutStateChecks(Packet packet) {
		ByteBuffer buffer = ByteBuffer.allocate(packet.size() + Integer.BYTES);
		buffer.putInt(protocol().computePacketID(packet));
		packet.write(buffer);
		buffer.flip();

		return other.getOrCreateConnection(manager).receive(buffer);
	}

	/** An internal method that receives and executes an incoming packet. */
	private CompletableFuture<Void> receive(ByteBuffer buffer) {
		Packet incoming = protocol().createPacket(buffer);

		CompletableFuture<Void> future = new CompletableFuture<>();
		
		manager.executeOn(() -> {
			protocol().handle(this, incoming);
			future.complete(null);
		}, incoming.handledBy());

		return future;
	}

	@Override
	public MemoryConnectionManager connectionManager() {
		return manager;
	}
}
