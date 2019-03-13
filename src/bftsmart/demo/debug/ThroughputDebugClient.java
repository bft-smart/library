/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.debug;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.tom.ServiceProxy;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ThroughputDebugClient {

	private static Logger logger;
	
	public static int clientId = 0;

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws IOException {
		
		
		if (args.length < 5) {
			logger.info(
					"Usage: ... ThroughputLatencyClient <initial client id> <number of clients> <number of operations> <request size> <interval (ms)>");
			System.exit(-1);
		}

		logger = LoggerFactory.getLogger(ThroughputDebugClient.class);
		
		clientId = Integer.parseInt(args[0]);
		int numThreads = Integer.parseInt(args[1]);

		int numberOfOps = Integer.parseInt(args[2]);
		int requestSize = Integer.parseInt(args[3]);
		int interval = Integer.parseInt(args[4]);
		
		Client[] clients = new Client[numThreads];

		for (int i = 0; i < numThreads; i++) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException ex) {

				ex.printStackTrace();
			}

			logger.info("Launching client " + (clientId + i));
			
			clients[i] = new ThroughputDebugClient.Client(clientId + i, numberOfOps, requestSize, interval);
		}

		final ThreadGroup tg = new ThreadGroup("Client Group Threads");
		ThreadFactory tf = new ThreadFactory() {
			AtomicInteger id = new AtomicInteger();

			@Override
			public Thread newThread(Runnable runnable) {
				return new Thread(tg, runnable, "ClientThread_" + id.getAndIncrement());
			}
		};

		ExecutorService exec = Executors.newFixedThreadPool(clients.length, tf);

		Collection<Future<?>> tasks = new LinkedList<>();

		for (Client c : clients) {
			tasks.add(exec.submit(c));
		}

		// wait for tasks completion
		for (Future<?> currTask : tasks) {
			try {
				currTask.get();
			} catch (InterruptedException | ExecutionException ex) {

				ex.printStackTrace();
			}

		}

		exec.shutdown();

		logger.info("All clients done.");
	}

	static class Client extends Thread {

		int id;
		int numberOfOps;
		int requestSize;
		int interval;
		
		ServiceProxy proxy;
		byte[] request;

		public Client(int id, int numberOfOps, int requestSize, int interval) {
			super("Client " + id);

			this.id = id;
			this.numberOfOps = numberOfOps;
			this.requestSize = requestSize;
			this.interval = interval;
			this.proxy = new ServiceProxy(id);
			this.request = new byte[this.requestSize];
		}

		public void run() {

			logger.info("Executing debug experiment for " + numberOfOps + " ops");

			for (int i = 0; i < numberOfOps; i++) {

				proxy.invokeOrdered(request);
				
				if (interval > 0) {
					try {
						// sleeps interval ms before sending next request
						Thread.sleep(interval);
					} catch (InterruptedException ex) {
					}
				}
			}
			proxy.close();
		}

	}
}