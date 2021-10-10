package brownshome.netcode.udp;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import brownshome.netcode.*;

/** Represents a UDP connection that is bound to a single port on the machine. */
public class UDPConnectionManager implements ConnectionManager<InetSocketAddress, UDPConnection> {
	private static final System.Logger LOGGER = System.getLogger(UDPConnectionManager.class.getModule().getName());
	private static final ThreadGroup UDP_SEND_THREAD_GROUP = new ThreadGroup("UDP-Send");

	public static final int BUFFER_SIZE = 16 * 1024 * 1024;

	private final List<Schema> schema;
	private final DatagramChannel channel;
	private final InetSocketAddress address;

	/** We drop the packet, this causes it to be resent later by the other end of the connection. */
	private static final class DroppingExecutor implements Executor {
		final AtomicInteger packetSlotsRemaining;
		final Executor executor;

		DroppingExecutor(Executor executor, int concurrency) {
			this.executor = executor;
			this.packetSlotsRemaining = new AtomicInteger(concurrency);
		}

		@Override
		public void execute(Runnable command) {
			if(packetSlotsRemaining.get() == 0) {
				throw new RejectedExecutionException("Not able to execute this command");
			}

			packetSlotsRemaining.incrementAndGet();

			executor.execute(() -> {
				try {
					command.run();
				} finally { packetSlotsRemaining.decrementAndGet(); }
			});
		}
	}

	private final Map<String, Executor> executors = new HashMap<>();
	private final Map<SocketAddress, UDPConnection> connections = new HashMap<>();

	private final Thread listenerThread;

	/** This is a single threaded executor that should be used to dispatch items to the channel. */
	private final ScheduledThreadPoolExecutor submissionThread = new ScheduledThreadPoolExecutor(1, task -> {
		var t = new Thread(UDP_SEND_THREAD_GROUP, task, "UDP-Send-" + address());
		t.setDaemon(true);
		return t;
	});

	public UDPConnectionManager(List<Schema> schema, int port) throws IOException {
		this.schema = schema;

		channel = DatagramChannel.open(StandardProtocolFamily.INET6);

		// Increase the buffer sizes to allow larger bursts of traffic.
		channel.setOption(StandardSocketOptions.SO_SNDBUF, BUFFER_SIZE);
		channel.setOption(StandardSocketOptions.SO_RCVBUF, BUFFER_SIZE);

		if (port == 0) {
			channel.bind(null);
			port = ((InetSocketAddress) channel.getLocalAddress()).getPort();
		} else {
			channel.bind(new InetSocketAddress(port));
		}

		address = new InetSocketAddress("::1", port);

		listenerThread = new Thread(() -> {
			ByteBuffer buffer = ByteBuffer.allocate(1024);

			while (true) {
				InetSocketAddress remoteAddress;

				try {
					remoteAddress = (InetSocketAddress) channel.receive(buffer);
				} catch (ClosedByInterruptException cbie) {
					//Exit
					LOGGER.log(System.Logger.Level.INFO, "Port {0} UDP listener shutting down", address.getPort());
					return;
				} catch (IOException e) {
					LOGGER.log(System.Logger.Level.ERROR, "Error waiting on socket", e);
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

	ScheduledThreadPoolExecutor submissionThread() {
		return submissionThread;
	}

	public UDPConnectionManager(List<Schema> schema) throws IOException {
		this(schema, 0);
	}

	@Override
	public UDPConnection getOrCreateConnection(InetSocketAddress other) {
		return connections.computeIfAbsent(other, o -> new UDPConnection(this, other));
	}

	@Override
	public void registerExecutor(String name, Executor executor, int concurrency) {
		executors.put(name, new DroppingExecutor(executor, concurrency));
	}

	void executeOn(Runnable runner, String thread) {
		executors.get(thread).execute(runner);
	}

	@Override
	public List<Schema> schemas() {
		return schema;
	}

	@Override
	public void close() {
		for (var connection : connections.values()) {
			connection.closeConnection();
		}

		for (var connection : connections.values()) {
			try {
				connection.closeConnection().get();
			} catch (InterruptedException e) {
				//Exit from the close operation.
				break;
			} catch (ExecutionException e) {
				LOGGER.log(System.Logger.Level.WARNING,
						String.format("Connection '%s' failed to terminate cleanly", connection.address()),
						e);
				//Keep trying to exit.
			}
		}

		listenerThread.interrupt();

		try {
			LOGGER.log(System.Logger.Level.INFO, "Shutting down submission thread for ''{0}''", address());
			submissionThread.shutdown();
			submissionThread.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) { /* Stop waiting */ }
	}

	@Override
	public CompletableFuture<Void> closeAsync() {
		return CompletableFuture.allOf(connections.values().stream().map(Connection::closeConnection).toArray(CompletableFuture[]::new)).whenComplete((unused, throwable) -> {
			LOGGER.log(System.Logger.Level.INFO, "Shutting down submission thread for ''{0}''", address());
			submissionThread.shutdown();
		}).thenRunAsync(() -> {
			try {
				submissionThread.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	DatagramChannel channel() {
		return channel;
	}

	@Override
	public InetSocketAddress address() {
		return address;
	}
}
