package brownshome.netcode.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import brownshome.netcode.Connection;
import brownshome.netcode.Packet;
import brownshome.netcode.Protocol;
import brownshome.netcode.packets.HelloPacket;
import brownshome.netcode.packets.NegotiateProtocolPacket;

public class MemoryConnection implements Connection<MemoryConnectionManager> {
	private final MemoryConnectionManager other;
	private final MemoryConnectionManager manager;
	
	private Future<Void> closed = null;
	private Future<Void> connected = null;
	
	private Protocol protocol = null;
	
	public MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		this.other = other;
		this.manager = manager;
		
		protocol = new Protocol(manager.schemas());
	}

	@Override
	public Future<Void> send(Packet packet) {
		ByteBuffer buffer = ByteBuffer.allocate(packet.size() + Integer.BYTES);
		buffer.putInt(protocol.computePacketID(packet));
		packet.write(buffer);
		buffer.flip();
		
		return other.getOrCreateConnection(manager).receive(buffer);
	}

	/** An internal method that receives and executes an incoming packet. */
	private Future<Void> receive(ByteBuffer buffer) {
		SettableFuture<Void> future = SettableFuture.create();
		Packet incoming = protocol.createPacket(buffer);
		
		manager.executeOn(() -> {
			protocol.handle(this, incoming);
			future.set(null);
		}, incoming.handledBy());
		
		return future;
	}

	@Override
	public Future<Void> flush() {
		return Futures.immediateFuture(null);
	}

	@Override
	public MemoryConnectionManager getAddress() {
		return other;
	}

	@Override
	public Future<Void> connect() {
		assert connected == null;
		
		connected = SettableFuture.create();
		
		send(new NegotiateProtocolPacket(manager.schemas()));
		
		return connected;
	}

	@Override
	public Future<Void> close() {
		assert closed == null;
		
		closed = Futures.immediateFuture(null);
		
		return closed;
	}

	@Override
	public MemoryConnectionManager getConnectionManager() {
		return manager;
	}

	@Override
	public Protocol getProtocol() {
		assert connected != null && connected.isDone();
		
		return protocol;
	}

	@Override
	public void setProtocol(Protocol networkProtocol) {
		this.protocol = networkProtocol;
		
		if(connected != null) {
			((SettableFuture<Void>) connected).set(null);
		} else {
			connected = Futures.immediateFuture(null);
		}
	}
}
