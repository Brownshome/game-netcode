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
public class UDPConnectionManager extends ConnectionManager<InetSocketAddress, UDPConnection> {
	private static final ThreadGroup UDP_SEND_THREAD_GROUP = new ThreadGroup("UDP-Send");

	public static final int BUFFER_SIZE = 16 * 1024 * 1024;

	private final DatagramChannel channel;
	private final InetSocketAddress address;

	private final Thread listenerThread;

	/** This is a single threaded executor that should be used to dispatch items to the channel. */
	private final ScheduledThreadPoolExecutor submissionThread = new ScheduledThreadPoolExecutor(1, task -> {
		var t = new Thread(UDP_SEND_THREAD_GROUP, task, "UDP-Send-" + address());
		t.setDaemon(true);
		return t;
	});

	public UDPConnectionManager(List<Schema> schema, int port) throws IOException {
		super(schema);

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
	protected final UDPConnection createNewConnection(InetSocketAddress inetSocketAddress) {
		return new UDPConnection(this, inetSocketAddress);
	}

	@Override
	public void close() throws InterruptedException {
		super.close();

		listenerThread.interrupt();

		LOGGER.log(System.Logger.Level.INFO, "Shutting down submission thread for ''{0}''", address());
		submissionThread.shutdown();
		submissionThread.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

		listenerThread.join();
	}

	@Override
	public CompletableFuture<Void> closeAsync() {
		return super.closeAsync().whenComplete((unused, throwable) -> {
			listenerThread.interrupt();
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
