package brownshome.netcode.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import brownshome.netcode.Connection;
import brownshome.netcode.ConnectionManager;
import brownshome.netcode.Schema;

/** Represents a UDP connection that is bound to a single port on the machine. */
public class UDPConnectionManager implements ConnectionManager<InetSocketAddress, UDPConnection> {
	private static final Logger LOGGER = Logger.getLogger("network");

	private final List<Schema> schema;
	private final DatagramChannel channel;
	private final InetSocketAddress address;

	private final Map<String, Executor> executors = new HashMap<>();
	private final Map<SocketAddress, UDPConnection> connections = new HashMap<>();

	private final Thread listenerThread;

	public UDPConnectionManager(List<Schema> schema, int port) throws IOException {
		this.schema = schema;

		channel = DatagramChannel.open(StandardProtocolFamily.INET6);

		if(port == 0) {
			channel.bind(null);
			port = ((InetSocketAddress) channel.getLocalAddress()).getPort();
		} else {
			channel.bind(new InetSocketAddress(port));
		}

		address = new InetSocketAddress("::1", port);

		listenerThread = new Thread(() -> {
			ByteBuffer buffer = ByteBuffer.allocate(1024);

			while(true) {
				InetSocketAddress remoteAddress;

				try {
					remoteAddress = (InetSocketAddress) channel.receive(buffer);
				} catch(ClosedByInterruptException cbie) {
					//Exit
					LOGGER.info(String.format("Port %d UDP listener shutting down", address.getPort()));
					return;
				} catch(IOException e) {
					LOGGER.log(Level.SEVERE, "Error waiting on socket", e);
					return;
				}

				if(remoteAddress == null)
					continue;

				buffer.flip();
				getOrCreateConnection(remoteAddress).receive(buffer);
				buffer.clear();
			}
		}, String.format("Port %d UDP listener", address.getPort()));

		listenerThread.start();
	}

	public UDPConnectionManager(List<Schema> schema) throws IOException {
		this(schema, 0);
	}

	@Override
	public UDPConnection getOrCreateConnection(InetSocketAddress other) {
		return connections.computeIfAbsent(other, o -> new UDPConnection(this, other));
	}

	@Override
	public void registerExecutor(String name, Executor executor) {
		executors.put(name, executor);
	}

	public void executeOn(Runnable runner, String thread) {
		executors.get(thread).execute(runner);
	}

	@Override
	public List<Schema> schemas() {
		return schema;
	}

	@Override
	public void close() {
		for(var connection : connections.values()) {
			connection.closeConnection(true);
		}

		for(var connection : connections.values()) {
			try {
				connection.closeConnection(true).get();
			} catch(InterruptedException e) {
				//Exit from the close operation.
				break;
			} catch(ExecutionException e) {
				LOGGER.log(Level.WARNING,
						String.format("Connection '%s' failed to terminate cleanly", connection.address()),
						e.getCause());
				//Keep trying to exit.
			}
		}

		listenerThread.interrupt();
	}

	public DatagramChannel channel() {
		return channel;
	}

	public InetSocketAddress getAddress() {
		return address;
	}
}
