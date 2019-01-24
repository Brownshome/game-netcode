package brownshome.lagatron;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.chart.XYChart.Data;

public class PassthroughHandler {
	static class Packet implements Comparable<Packet> {
		int timeToSend;
		int timeRecieved;
		byte[] data;
		SocketAddress address;
		
		Packet(int timeToSend, int timeRecieved, byte[] data) {
			this.timeRecieved = timeRecieved;
			this.timeToSend = timeToSend;
			this.data = data;
		}
		
		Packet(int timeToSend, int timeRecieved, DatagramPacket packet) {
			this(timeToSend, timeRecieved, new byte[packet.getLength()]);
			System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
		}

		@Override
		public int compareTo(Packet o) {
			return timeToSend - o.timeToSend;
		}

		public boolean isValid() {
			return (MTULimit == 0 || data.length <= MTULimit) && Math.random() >= packetLoss;
		}
	}
	
	//if !bidirectional packets only go from the client to the server
	static SocketAddress serverAddress;
	static SocketAddress clientAddress;
	
	static DatagramSocket serverSideSocket;
	static DatagramSocket clientSideSocket;
	
	static Map<Integer, Integer> uploadPacketSizes = Collections.synchronizedMap(new HashMap<>());
	static Map<Integer, Integer> downloadPacketSizes = Collections.synchronizedMap(new HashMap<>());
	
	static Queue<Packet> uploadBandwidth = new ArrayDeque<>();
	static Queue<Packet> downloadBandwidth = new ArrayDeque<>();
	
	static double usedUpload;
	static double usedDownload;
	
	static PriorityQueue<Packet> uploadQueue = new PriorityQueue<>();
	static PriorityQueue<Packet> downloadQueue = new PriorityQueue<>();
	
	static double bandwidthLimit;
	static int MTULimit;
	static int latency;
	static int jitter;
	static boolean biDirectional;
	static double packetLoss;
	
	static volatile boolean running;
	
	static List<Thread> threads = new ArrayList<>();
	final static ExecutorService startStopHandler = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "START STOP THREAD");
		t.setDaemon(true);
		return t;
	});
	
	public static void startPassthrough(InetSocketAddress destinationAddress, int incommingPort, double bandwidthLimit,
			int MTULimit, int latency, int jitter, boolean biDirectional, double packetLoss) {
		
		startStopHandler.execute(() -> startPassthroughImpl(destinationAddress, incommingPort, bandwidthLimit,
				MTULimit, latency, jitter, biDirectional, packetLoss));
	}
	
	
	static void startPassthroughImpl(InetSocketAddress destinationAddress, int incommingPort, double bandwidthLimit,
			int MTULimit, int latency, int jitter, boolean biDirectional, double packetLoss) {
		
		//kill existing threads
		running = false;
		
		while(true) {
			boolean allDead = true;
			for(Thread thread : threads) {
				if(thread.isAlive()) {
					thread.interrupt();
					allDead = false;
				}
			}
			
			if(!allDead) {
				synchronized(PassthroughHandler.class) {
					try { PassthroughHandler.class.wait(); } catch (InterruptedException e) {}
				}
			} else break;
		}
		
		running = true;
		clientAddress = null;
		
		if(clientSideSocket != null) clientSideSocket.close();
		if(serverSideSocket != null) serverSideSocket.close();
		
		try { clientSideSocket = new DatagramSocket(incommingPort); } catch (SocketException e) {
			System.err.println("Unable to bind to port " + incommingPort);
		}
		
		try { serverSideSocket = new DatagramSocket(); } catch(Exception e) {
			System.err.println("Unable to create server side socket.");
		}
		
		uploadPacketSizes.clear();
		downloadPacketSizes.clear();
		uploadBandwidth.clear();
		downloadBandwidth.clear();
		uploadQueue.clear();
		downloadBandwidth.clear();
		
		serverAddress = destinationAddress;
		PassthroughHandler.bandwidthLimit = bandwidthLimit;
		PassthroughHandler.MTULimit = MTULimit;
		PassthroughHandler.latency = latency;
		PassthroughHandler.jitter = jitter;
		PassthroughHandler.biDirectional = biDirectional;
		PassthroughHandler.packetLoss = packetLoss;
		
		Consumer<DatagramPacket> uploadFunc = p -> {
			Packet packet = new Packet(getSendTime(), getCurrentTime(), p);
			packet.address = p.getSocketAddress();
			
			synchronized(uploadQueue) {
				uploadQueue.add(packet);
				uploadQueue.notify();
			}
			
			synchronized(uploadBandwidth) {
				uploadBandwidth.add(packet);
			}
			
			uploadPacketSizes.merge(packet.data.length, 1, Integer::sum);
			
			if(clientAddress == null) {
				clientAddress = packet.address;
			}
		};
		
		Thread clientListener = new Thread(() -> listen(clientSideSocket, uploadFunc), "CLIENT LISTENER");
		clientListener.setDaemon(true);
		threads.add(clientListener);
		clientListener.start();
				
		Thread upload = new Thread(PassthroughHandler::upload, "UPLOADER");
		upload.setDaemon(true);
		threads.add(upload);
		upload.start();
		
		if(biDirectional) {
			Consumer<DatagramPacket> downloadFunc = p -> {
				Packet packet = new Packet(getSendTime(), getCurrentTime(), p);
				
				synchronized(downloadQueue) {
					downloadQueue.add(packet);
					downloadQueue.notify();
				}
				
				synchronized(downloadBandwidth) {
					downloadBandwidth.add(packet);
				}
				
				downloadPacketSizes.merge(packet.data.length, 1, Integer::sum);
			};
			Thread serverListener = new Thread(() -> listen(serverSideSocket, downloadFunc), "SERVER LISTENER");
			serverListener.setDaemon(true);
			threads.add(serverListener);
			serverListener.start();
			
			Thread download = new Thread(PassthroughHandler::download, "DOWNLOADER");
			download.setDaemon(true);
			threads.add(download);
			download.start();
		}
		
		Thread chartCalculator = new Thread(PassthroughHandler::doCharts, "CHART CALCULATOR");
		chartCalculator.setDaemon(true);
		threads.add(chartCalculator);
		chartCalculator.start();
		
		Platform.runLater(Lagatron.controller::enableButton);
	}

	static void doCharts() {
		while(running) {
			try { Thread.sleep(50);	} catch (InterruptedException e) { break; }
			
			int uploadedPackets = uploadPacketSizes.values().stream().reduce(0, Integer::sum);
			int downloadPackets = downloadPacketSizes.values().stream().reduce(0, Integer::sum);
			
			Collection<Data<String, Number>> upData = uploadPacketSizes.entrySet().stream().map(e -> new Data<String, Number>(Integer.toString(e.getKey()), e.getValue() * 100 / uploadedPackets)).collect(Collectors.toList());
			
			Platform.runLater(() -> {
				Lagatron.controller.uploadSizes.getData().setAll(upData);
			});
			
			synchronized(uploadBandwidth) {
				while(uploadBandwidth.peek() != null && uploadBandwidth.peek().timeRecieved - getCurrentTime() + 1000 < 0) {
					uploadBandwidth.remove();
				}
				
				usedUpload = uploadBandwidth.stream().mapToLong(packet -> packet.data.length).sum() / 1000;
			}
			
			synchronized(downloadBandwidth) {
				while(downloadBandwidth.peek() != null && downloadBandwidth.peek().timeRecieved - getCurrentTime() + 1000 < 0) {
					downloadBandwidth.remove();
				}
				
				usedDownload = downloadBandwidth.stream().mapToLong(packet -> packet.data.length).sum() / 1000;
			}
		}
		
		synchronized(PassthroughHandler.class) {
			PassthroughHandler.class.notify();
		}
	}
	
	static void upload() {
		synchronized(uploadQueue) {
			loop:
			while(running) {
				Packet packet = uploadQueue.peek();
				int t = getCurrentTime();
				
				if(packet == null || packet.timeToSend - t > 0) {
					//not ready to send
					try { 
						uploadQueue.wait(packet == null ? 0 : packet.timeToSend - t);
					} catch (InterruptedException e) { 
						break; 
					}
					
					continue;
				}
				
				//send packet
				uploadQueue.poll();
				try {
					if(packet.isValid()) 
						serverSideSocket.send(new DatagramPacket(packet.data, packet.data.length, serverAddress));
				} catch (IOException e) {
					System.err.println("Unable to send packet\n" + e.getMessage());
				}
				
				//b / kbits-1 / 1000 * 1000000000 = ns
				if(bandwidthLimit != 0) {
					long nanosToSleepFor = (long) (packet.data.length * 1000000.0 / bandwidthLimit);
					long start = System.nanoTime();
					long tmp;
					while((tmp = System.nanoTime()) < start + nanosToSleepFor) {
						long delta = start + nanosToSleepFor - tmp;
						try { uploadQueue.wait(delta / 1000000, (int) (delta % 1000000)); } catch (InterruptedException e) { break loop; }
					}
				}
			}
		
			synchronized(PassthroughHandler.class) {
				PassthroughHandler.class.notify();
			}
		}
	}
	
	static void download() {
		synchronized(downloadQueue) {
			loop:
			while(running) {
				Packet packet = downloadQueue.peek();
				int t = 0;
				if(packet == null || packet.timeToSend - (t = getCurrentTime()) > 0) {
					//not ready to send
					try { downloadQueue.wait(packet == null ? 0 : packet.timeToSend - t); } catch (InterruptedException e) { break; }
					continue;
				}
				
				//send packet
				downloadQueue.poll();
				try {
					if(packet.isValid()) 
						serverSideSocket.send(new DatagramPacket(packet.data, packet.data.length, clientAddress));
				} catch (IOException e) {
					System.err.println("Unable to send packet\n" + e.getMessage());
				}

				//b / kbits-1 / 1000 * 1000000000 = ns
				if(bandwidthLimit != 0) {
					long nanosToSleepFor = (long) (packet.data.length * 1000000.0 / bandwidthLimit);
					long start = System.nanoTime();
					long tmp;
					while((tmp = System.nanoTime()) < start + nanosToSleepFor) {
						long delta = start + nanosToSleepFor - tmp;
						try { downloadQueue.wait(delta / 1000000, (int) (delta % 1000000)); } catch (InterruptedException e) { break loop; }
					}
				}
			}
		
			synchronized(PassthroughHandler.class) {
				PassthroughHandler.class.notify();
			}
		}
	}
	
	static void listen(DatagramSocket socket, Consumer<DatagramPacket> func) {
		DatagramPacket packet = new DatagramPacket(new byte[65565], 65565);
		
		while(running) {
			try {
				socket.receive(packet);
				func.accept(packet);
			} catch(SocketException se) {
				break;
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}
		
		synchronized(PassthroughHandler.class) {
			PassthroughHandler.class.notify();
		}
	}

	private static int getCurrentTime() {
		return (int) (System.nanoTime() / 1000000);
	}

	private static int getSendTime() {
		return getCurrentTime() + latency + (int) ((Math.random() * 2 - 1) * jitter);
	}
}
