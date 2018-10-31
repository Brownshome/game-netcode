package brownshome.netcode.memory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import brownshome.netcode.*;

public class MemoryConnection implements Connection<MemoryConnectionManager> {

	/**
	 * This is a struct that is used to store ordering guarantees. Orders are guaranteed against the full schema name
	 * and the ID of the packet within that schema.
	 */
	private static class OrderingBlock {
		final String schemaName;
		final int id;

		OrderingBlock(String schemaName, int id) {
			this.schemaName = schemaName;
			this.id = id;
		}

		OrderingBlock(Packet packet) {
			this(packet.schemaName(), packet.packetID());
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			OrderingBlock that = (OrderingBlock) o;
			return id == that.id && schemaName.equals(that.schemaName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(schemaName, id);
		}
	}

	private final MemoryConnectionManager other;
	private final MemoryConnectionManager manager;

	private final Protocol protocol;
	private final ConnectionFlusher flusher;

	private CompletableFuture<Void> closedFuture;
	private final ReentrantReadWriteLock closingLock;

	/** This mapping stores the list
	 * This is a thread-safe data structure
	 * */
	private final ConcurrentHashMap<OrderingBlock, Collection<CompletableFuture<Void>>> orderingMapping;
	
	protected MemoryConnection(MemoryConnectionManager manager, MemoryConnectionManager other) {
		this.other = other;
		this.manager = manager;

		//The order of these operands does not matter.
		Protocol.ProtocolNegotiation negotiationResult = Protocol.negotiateProtocol(other.schemas(), manager.schemas());
		this.protocol = negotiationResult.protocol;

		orderingMapping = new ConcurrentHashMap<>();
		flusher = new ConnectionFlusher(this);

		flusher.start();
		closedFuture = null;
		closingLock = new ReentrantReadWriteLock();
	}

	@Override
	public CompletableFuture<Void> send(Packet packet) {
		try { closingLock.readLock().lock();

			if(closedFuture != null) {
				return CompletableFuture.failedFuture(new NetworkException("This connection is closed", this));
			}

			//1. Check for any ordering barriers to computation.

			//1a. Get all of the packets that have occurred before this one that we care about.
			List<CompletableFuture<Void>> allOf = new ArrayList<>();
			for(int blockedBy : packet.orderedIds()) {
				OrderingBlock block = new OrderingBlock(packet.schemaName(), blockedBy);
				var futures = orderingMapping.get(block);
				if(futures != null) {
					allOf.addAll(orderingMapping.get(block));
				}
			}

			//1b. Only send the packet after all of them have completed.
			CompletableFuture<Void> startingFuture = allOf.isEmpty()
					? CompletableFuture.completedFuture(null)
					: CompletableFuture.allOf(allOf.toArray(new CompletableFuture<?>[0]));

			CompletableFuture<Void> handlerCompleteFuture = new CompletableFuture<>();
			
			startingFuture.thenRun(() -> {
				//Send the packet
				other.executeOn(() -> {
							try {
								protocol().handle(this, packet);
							} finally {
								handlerCompleteFuture.complete(null);
							}
						}, packet.handledBy());
			});

			//1c. Add the post action future to the list of futures.
			OrderingBlock thisBlock = new OrderingBlock(packet);
			orderingMapping.compute(thisBlock, (key, oldList) -> {
				//Remove all of the futures that have completed, but retain those that have not, and the new future.
				//Do not edit the old list...

				Collection<CompletableFuture<Void>> newList = new ArrayList<>();

				newList.add(handlerCompleteFuture);

				if(oldList != null) {
					for(var f : oldList) {
						if(!f.isDone()) {
							newList.add(f);
						}
					}
				}

				return newList;
			});

			//2. Submit the flusher task
			//3. Return the correct future
			if(packet.reliable()) {
				flusher.submitFuture(handlerCompleteFuture);
				return handlerCompleteFuture;
			} else {
				return startingFuture;
			}
		} finally { closingLock.readLock().unlock(); }
	}

	@Override
	public CompletableFuture<Void> flush() {
		return flusher.flush();
	}

	@Override
	public MemoryConnectionManager address() {
		return other;
	}

	@Override
	public CompletableFuture<Void> connect() {
		try { closingLock.readLock().lock();

			if(closedFuture != null) {
				return CompletableFuture.failedFuture(new NetworkException("This connection is closed", this));
			}

			return CompletableFuture.completedFuture(null);
		} finally { closingLock.readLock().unlock(); }
	}

	@Override
	public CompletableFuture<Void> closeConnection() {
		try { closingLock.writeLock().lock();
			if(closedFuture == null) {
				//TODO tell the other connection to shutdown

				closedFuture = flush().thenRun(() -> {
					//Close down everything
					//Stop the flusher
					flusher.interrupt();

					//Remove this connection from the queue
					//TODO removeConnection();
				});
			}

			return closedFuture;
		} finally { closingLock.writeLock().unlock(); }
	}

	@Override
	public MemoryConnectionManager connectionManager() {
		return manager;
	}

	@Override
	public Protocol protocol() {
		return protocol;
	}
}
