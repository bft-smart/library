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

import bftsmart.tom.ServiceProxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example client that updates a BFT replicated service (a counter).
 * 
 * @author alysson
 */
public class CounterClient {

	private static final Logger log = LoggerFactory.getLogger(CounterClient.class);

	
	
	
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			log.error("Usage: java CounterClient <process id> <increment> [<number of operations>] \n "
					+ "\t if <increment> equals 0 the request will be read-only \n"
					+ "\t default <number of operations> equals 1000");
			log.error("Example: bash runscripts/smartrun.sh bftsmart.demo.counter.CounterClient 1001 1 1000");
			System.exit(-1);
		}

		ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));
		
		try {
			log.trace("Waiting 2 seconds before issue operations.");
			Thread.sleep(2000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {

			int inc = Integer.parseInt(args[1]);
			int numberOfOps = (args.length > 2) ? Integer.parseInt(args[2]) : 1000;

			long startTime = System.nanoTime();
			
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
					//break;
				}
			}

			long endTime = System.nanoTime();
			long duration = (endTime - startTime) / 1000000 / 1000;
			if(duration >0) {
			double opsPerSecond = numberOfOps / duration;
				log.info("Duration time: {}, operations per second: {}", duration, opsPerSecond);
			}
					
			System.exit(0);

		} catch (IOException | NumberFormatException e) {
			counterProxy.close();
			System.exit(-1);
		}
	}
}
