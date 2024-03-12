package bftsmart.benchmark;

import bftsmart.tom.ServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;

public class BenchmarkClient {
	private static final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private static final Logger logger = LoggerFactory.getLogger("benchmarking");
	private static int initialClientId;
	private static byte[] request;

	public static void main(String[] args) throws InterruptedException {
		if (args.length != 7) {
			String arguments = "<initial client id> <num clients> <number of operations per client> <request size> " +
					"<isWrite?> <use hashed response> <measurement leader?>";
			System.out.println("USAGE: benchmark.BenchmarkClient " + arguments);
			System.exit(-1);
		}
		initialClientId = Integer.parseInt(args[0]);
		int numClients = Integer.parseInt(args[1]);
		int numOperationsPerClient = Integer.parseInt(args[2]);
		int requestSize = Integer.parseInt(args[3]);
		boolean isWrite = Boolean.parseBoolean(args[4]);
		boolean useHashedResponse = Boolean.parseBoolean(args[5]);
		boolean measurementLeader = Boolean.parseBoolean(args[6]);

		request = new byte[requestSize];
		SecureRandom random = new SecureRandom("test".getBytes());
		random.nextBytes(request);

		CountDownLatch latch = new CountDownLatch(numClients);
		Client[] clients = new Client[numClients];
		for (int i = 0; i < numClients; i++) {
			int clientId = initialClientId + i;
			clients[i] = new Client(clientId, numOperationsPerClient, isWrite, useHashedResponse, measurementLeader, latch);
			clients[i].start();
			Thread.sleep(10);
		}

		latch.await();
		System.out.println("Executing experiment");
	}

	private static class Client extends Thread {

		private final int clientId;
		private final int numOperations;
		private final boolean isWrite;
		private final boolean useHashedResponse;
		private final boolean measurementLeader;
		private final CountDownLatch latch;
		private final ServiceProxy proxy;

		public Client(int clientId, int numOperations, boolean isWrite, boolean useHashedResponse,
					  boolean measurementLeader, CountDownLatch latch) {

			this.clientId = clientId;
			this.numOperations = numOperations;
			this.isWrite = isWrite;
			this.useHashedResponse = useHashedResponse;
			this.measurementLeader = measurementLeader;
			this.latch = latch;
			this.proxy = new ServiceProxy(clientId);
		}

		@Override
		public void run() {
			try {
				latch.countDown();
				for (int i = 0; i < numOperations; i++) {
					long start, end, delta;
					start = System.nanoTime();
					if (isWrite) {
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
					end = System.nanoTime();
					delta = end - start;

					if (initialClientId == clientId && measurementLeader) {
						measurementLogger.info("M-global: {}", delta);
					}
				}
			} finally {
				proxy.close();
			}
		}
	}
}
