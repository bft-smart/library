package bftsmart.benchmark;

import bftsmart.tom.ServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * @author robin
 */
public class ThroughputLatencyClient {
	private static final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private static int initialClientId;
	private static byte[] request;

	public static void main(String[] args) throws InterruptedException {
		if (args.length != 7) {
			System.out.println("USAGE: bftsmart.benchmark.ThroughputLatencyClient <initial client id> " +
					"<num clients> <number of operations per client> <request size> <sendOrderedRequest?> <use hashed response> " +
					"<measurement leader?>");
			System.exit(-1);
		}

		initialClientId = Integer.parseInt(args[0]);
		int numClients = Integer.parseInt(args[1]);
		int numOperationsPerClient = Integer.parseInt(args[2]);
		int requestSize = Integer.parseInt(args[3]);
		boolean sendOrderedRequest = Boolean.parseBoolean(args[4]);
		boolean useHashedResponse = Boolean.parseBoolean(args[5]);
		boolean measurementLeader = Boolean.parseBoolean(args[6]);
		CountDownLatch latch = new CountDownLatch(numClients);
		Client[] clients = new Client[numClients];
		request = new byte[requestSize];
		for (int i = 0; i < requestSize; i++) {
			request[i] = (byte) i;
		}

		for (int i = 0; i < numClients; i++) {
			clients[i] = new Client(initialClientId + i,
					numOperationsPerClient, sendOrderedRequest, useHashedResponse, measurementLeader, latch);
			clients[i].start();
			Thread.sleep(10);
		}

		latch.await();
		System.out.println("Executing experiment");
	}

	private static class Client extends Thread {
		private final int clientId;
		private final int numOperations;
		private final boolean sendOrderedRequest;
		private final ServiceProxy proxy;
		private final CountDownLatch latch;
		private final boolean useHashedResponse;
		private final boolean measurementLeader;

		public Client(int clientId, int numOperations, boolean sendOrderedRequest, boolean useHashedResponse,
					  boolean measurementLeader, CountDownLatch latch) {
			this.clientId = clientId;
			this.numOperations = numOperations;
			this.sendOrderedRequest = sendOrderedRequest;
			this.useHashedResponse = useHashedResponse;
			this.measurementLeader = measurementLeader;
			this.proxy = new ServiceProxy(clientId);
			this.latch = latch;
		}

		@Override
		public void run() {
			try {
				latch.countDown();
				for (int i = 0; i < numOperations; i++) {
					long t1, t2, latency;
					t1 = System.nanoTime();
					if (sendOrderedRequest) {
						if (useHashedResponse) {
							proxy.invokeOrderedHashed(request);
						} else {
							proxy.invokeOrdered(request);
						}
					} else {
						if (useHashedResponse) {
							proxy.invokeUnorderedHashed(request);
						} else {
							proxy.invokeUnordered(request);
						}
					}
					t2 = System.nanoTime();
					latency = t2 - t1;

					if (initialClientId == clientId && measurementLeader) {
						measurementLogger.info("M-global: {}", latency);
					}
				}
			} finally {
				proxy.close();
			}
		}
	}
}
