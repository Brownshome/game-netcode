package brownshome.netcode.memory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MemoryPipe {
	private MemoryPipe other;
	private final BlockingQueue<Runnable> send;
	private final BlockingQueue<Runnable> receive;
	
	private MemoryPipe(BlockingQueue<Runnable> send, BlockingQueue<Runnable> receive) {
		this.send = send;
		this.receive = receive;
	}
	
	public static synchronized MemoryPipe createPipes() {
		BlockingQueue<Runnable> a = new LinkedBlockingQueue<>();
		BlockingQueue<Runnable> b = new LinkedBlockingQueue<>();
		
		MemoryPipe result = new MemoryPipe(a, b);
		MemoryPipe slave = new MemoryPipe(b, a);
		
		result.other = slave;
		slave.other = result;
		
		return result;
	}
	
	public MemoryPipe getOther() {
		return other;
	}
	
	public void send(Runnable r) {
		send.add(r);
	}
	
	public Runnable getRunner() {
		return () -> {
			while(true) {
				try {
					receive.take().run();
				} catch (InterruptedException e) {
					return;
				}
			}
		};
	}
	
	public int countTasks() {
		return receive.size();
	}
	
	public void executeTask() throws InterruptedException {
		receive.take().run();
	}
}
