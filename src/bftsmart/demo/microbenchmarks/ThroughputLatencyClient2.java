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
package bftsmart.demo.microbenchmarks;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Storage;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ThroughputLatencyClient2 {

	public static int initId = 0;
	private static Logger logger = LoggerFactory.getLogger(ThroughputLatencyClient2.class);
	
	@SuppressWarnings("static-access")
	public static void main(String[] args) throws IOException {
		if (args.length < 6) {
			logger.info(
					"Usage: ... ThroughputLatencyClient "
					+ "<initial client id> "
					+ "<number of clients> "
					+ "<number of operations> "
					+ "<request size> "
					+ "<interval (ms)> "
					+ "<read only?> ");
			System.exit(-1);
		}

		initId = Integer.parseInt(args[0]);
		int numThreads = Integer.parseInt(args[1]);
		int numberOfOps = Integer.parseInt(args[2]);
		int requestSize = Integer.parseInt(args[3]);
		int interval = Integer.parseInt(args[4]);
		boolean readOnly = Boolean.parseBoolean(args[5]);
		
		Client[] clients = new Client[numThreads];

		logger.info("Number of threads: {}", numThreads);

		for (int i = 0; i < numThreads; i++) {
			try {
				Thread.sleep(120);
			} catch (InterruptedException ex) {

				ex.printStackTrace();
			}

			logger.info("Launching client: {} " ,  (initId + i));
			clients[i] = new ThroughputLatencyClient2.Client(initId + i, numberOfOps, requestSize, interval, readOnly);
		}

		ExecutorService exec = Executors.newFixedThreadPool(clients.length);
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
		boolean readOnly;
		ServiceProxy proxy;
		byte[] request;

		public Client(int id, int numberOfOps, int requestSize, int interval, boolean readOnly) {
			super("Client " + id);

			this.id = id;
			this.numberOfOps = numberOfOps;
			this.requestSize = requestSize;
			this.interval = interval;
			this.readOnly = readOnly;
			this.proxy = new ServiceProxy(id);
			this.request = new byte[this.requestSize];
			
		}

		public void run() {
			
			/*try {
				logger.info("Waiting before warm up.");
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}*/

			logger.info("Warm up...");

			int req = 0;
			
			for (int i = 0; i < numberOfOps / 2; i++, req++) {
						
					logger.debug("Client {}, sending req {} ...", this.id, req);
					
					
					byte [] reply = null;
					if (readOnly)
						reply = proxy.invokeUnordered(request);
					else
						reply = proxy.invokeOrdered(request);
					
					logger.trace("Client: {}, sent req: {}!", this.id, req);
					
					if (reply != null) {
						int newValue = -1;
						try {
							newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
						} catch (IOException e) {
							e.printStackTrace();
						}
						
						logger.trace("Client: {}, req: {}, returned value: {}.", new Object[] {this.id, i, newValue});
						
						if (logger.isDebugEnabled() && (req % 1000 == 0))
							logger.debug("Client: {}, req: {}, returned value: {}.", new Object[] {this.id, i, newValue});
					} 
					else {
						logger.error("Client: {}, req: {}, returned null reply.", this.id, i);
					}
				

				if (interval > 0) {
					try {
						// sleeps interval ms before sending next request
						Thread.sleep(interval);
					} catch (InterruptedException ex) {
					}
				}
			}
			logger.info("Warm up... DONE!");

			Storage st = new Storage(numberOfOps / 2);

			logger.info("Executing experiment for {} ops", (numberOfOps / 2));

			for (int i = 0; i < numberOfOps / 2; i++, req++) {
				long last_send_instant = System.nanoTime();
				
				byte [] reply = null;
				if (readOnly)
					reply = proxy.invokeUnordered(request);
				else
					reply = proxy.invokeOrdered(request);
				
				logger.trace("Client: {}, sent req: {}!", this.id, req);
				
				if (reply != null) {
					int newValue = -1;
					try {
						newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					logger.trace("Client: {}, req: {}, returned value: {}.", new Object[] {this.id, i, newValue});
					
					if (logger.isDebugEnabled() && (req % 1000 == 0))
						logger.debug("Client: {}, req: {}, returned value: {}.", new Object[] {this.id, i, newValue});
				} 
				else {
					logger.error("Client: {}, req: {}, returned null reply.", this.id, i);
				}
				
				st.store(System.nanoTime() - last_send_instant);

				if (interval > 0) {
					try {
						// sleeps interval ms before sending next request
						Thread.sleep(interval);
					} catch (InterruptedException ex) {
					}
				}

				if (logger.isDebugEnabled() && (req % 1000 == 0))
					logger.debug("Client {}, issued req: {}", this.id, req );
			}
			logger.info("Done experiment, Number of ops: " + ( numberOfOps / 2));

			if (id == initId) {
				logger.info("" + this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = "
						+ st.getAverage(true) / 1000 + " us ");
				logger.info("" + this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = "
						+ st.getDP(true) / 1000 + " us ");
				logger.info("" + this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = "
						+ st.getAverage(false) / 1000 + " us ");
				logger.info("" + this.id + " // Standard desviation for " + numberOfOps / 2
						+ " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
				logger.info("" + this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = "
						+ st.getMax(false) / 1000 + " us ");
			}
			System.exit(0);

			proxy.close();
		}
	}
}
