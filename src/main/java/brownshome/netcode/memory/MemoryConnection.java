package brownshome.netcode.memory;

import java.util.concurrent.Future;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import brownshome.netcode.Connection;
import brownshome.netcode.MemoryConnectionManager;
import brownshome.netcode.NetworkProtocol;
import brownshome.netcode.Packet;
import brownshome.netcode.PacketDefinition;

public class MemoryConnection implements Connection<MemoryConnectionManager> {
	private final MemoryConnectionManager other;
	private final MemoryConnectionManager manager;
	
	private Future<Void> closed = null;
	private Future<Void> connected = null;
	private NetworkProtocol protocol = null;
	
	public MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		this.other = other;
		this.manager = manager;
	}

	@Override
	public Future<Void> send(Packet packet) {
		SettableFuture<Void> result = SettableFuture.create();
		
		PacketDefinition<?> definition = manager.getGlobalProtocol().getDefinition(packet);
		
		other.executeOn(() -> {
			packet.handle(other.getConnection(manager));
			result.set(null);
		}, definition.handledBy);
		
		return result;
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
		send(getConnectionManager().getGlobalProtocol().getConnectPacket());
		
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
	public NetworkProtocol getProtocol() {
		assert connected != null && connected.isDone();
		
		return protocol;
	}

	@Override
	public void setProtocol(NetworkProtocol networkProtocol) {
		this.protocol = networkProtocol;
		
		if(connected != null) {
			((SettableFuture<Void>) connected).set(null);
		} else {
			connected = Futures.immediateFuture(null);
		}
	}
}
