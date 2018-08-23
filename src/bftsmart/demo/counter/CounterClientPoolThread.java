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
package bftsmart.demo.counter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.tom.ServiceProxy;

/**
 * Example client that updates a BFT replicated service (a counter).
 * 
 * @author alysson
 * @param <T>
 */
public class CounterClientPoolThread<T> {

	private static final Logger log = LoggerFactory.getLogger(CounterClientPoolThread.class);

	private static ScheduledExecutorService executor = null;
	private static SingletonTask task;

	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			log.error(
					"Usage: java CounterClient <process id> <increment> <number of clients> [<number of operations>] \n "
							+ "\t if <increment> equals 0 the request will be read-only \n"
							+ "\t default <number of operations> equals 1000");
			log.error("Example: bash runscripts/smartrun.sh bftsmart.demo.counter.CounterClient 1001 15 1 1000");
			System.exit(-1);
		}

		log.info("Initializing thread pool.");

		final ThreadGroup tg = new ThreadGroup("Scheduled Task Threads");
		ThreadFactory f = new ThreadFactory() {
			AtomicInteger id = new AtomicInteger();

			@Override
			public Thread newThread(Runnable runnable) {
				return new Thread(tg, runnable, "Scheduled-" + id.getAndIncrement());
			}
		};

		executor = Executors.newScheduledThreadPool(8, f);

		ArrayList<SingletonTask> tasks = new ArrayList<SingletonTask>();
		
		int clientID = Integer.parseInt(args[0]);
		int numberOfClients = Integer.parseInt(args[1]);
		int inc = Integer.parseInt(args[2]);
		int numberOfOps = Integer.parseInt(args[3]);

		for (int i = 0; i < numberOfClients; i++) {
			
			final int client = clientID + i;
			
			log.info("Creating task for clientID: {}!", client);
			
			tasks.add(new SingletonTask(executor, new Runnable() {				
				
				ServiceProxy counterProxy = new ServiceProxy(client);
				
				@Override
				public void run() {
					log.info("I am alive and running, clientID: {}!", client);
					try {
						// long startTime = System.nanoTime();

						for (int i = 0; i < numberOfOps; i++) {
							ByteArrayOutputStream out = new ByteArrayOutputStream(4);
							new DataOutputStream(out).writeInt(inc);

							// magic happens here
							byte[] reply = (inc == 0) ? counterProxy.invokeUnordered(out.toByteArray())
									: counterProxy.invokeOrdered(out.toByteArray());

							if (reply != null) {
								int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
								log.debug("Invocation: {}, returned value: {}", i, newValue);
							} else {
								log.error("Problem! Exiting.");
								break;
							}
						}

						/*
						 * long endTime = System.nanoTime(); long duration = (endTime - startTime) /
						 * 1000000 / 1000; double opsPerSecond = numberOfOps / duration;
						 * log.info("Duration time: {}, operations per second: {}", duration,
						 * opsPerSecond);
						 */

						//System.exit(0);

					} catch (IOException | NumberFormatException e) {
						counterProxy.close();
						System.exit(-1);
					}
				}
				
			}));
		
			
		}
		
		for (Iterator iterator = tasks.iterator(); iterator.hasNext();) {
			SingletonTask task = (SingletonTask) iterator.next();			
			task.reschedule(60, TimeUnit.SECONDS);
		}
		
		
	}

}
