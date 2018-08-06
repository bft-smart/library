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

import java.io.IOException;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Storage;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ThroughputLatencyClient {

    public static int initId = 0;
    
    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 7) {
            System.out.println("Usage: ... ThroughputLatencyClient <initial client id> <number of clients> <number of operations> <request size> <interval (ms)> <read only?> <verbose?>");
            System.exit(-1);
        }

        initId = Integer.parseInt(args[0]);
        int numThreads = Integer.parseInt(args[1]);

        int numberOfOps = Integer.parseInt(args[2]);
        int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[4]);
        boolean readOnly = Boolean.parseBoolean(args[5]);
        boolean verbose = Boolean.parseBoolean(args[6]);

        Client[] clients = new Client[numThreads];
        
        for(int i=0; i<numThreads; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                
                ex.printStackTrace();
            }
            
            System.out.println("Launching client " + (initId+i));
            clients[i] = new ThroughputLatencyClient.Client(initId+i,numberOfOps,requestSize,interval,readOnly, verbose);
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

        System.out.println("All clients done.");
    }

    static class Client extends Thread {

        int id;
        int numberOfOps;
        int requestSize;
        int interval;
        boolean readOnly;
        boolean verbose;
        ServiceProxy proxy;
        byte[] request;
        
        public Client(int id, int numberOfOps, int requestSize, int interval, boolean readOnly, boolean verbose) {
            super("Client "+id);
        
            this.id = id;
            this.numberOfOps = numberOfOps;
            this.requestSize = requestSize;
            this.interval = interval;
            this.readOnly = readOnly;
            this.verbose = verbose;
            this.proxy = new ServiceProxy(id);
            this.request = new byte[this.requestSize];
        }

        public void run() {

            System.out.println("Warm up...");

            int req = 0;
            
            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                if (verbose) System.out.print("Sending req " + req + "...");

                if(readOnly)
                        proxy.invokeUnordered(request);
                else
                        proxy.invokeOrdered(request);
                        
                if (verbose) System.out.println(" sent!");

                if (verbose && (req % 1000 == 0)) System.out.println(this.id + " // " + req + " operations sent!");

		if (interval > 0) {
                    try {
                        //sleeps interval ms before sending next request
                        Thread.sleep(interval);
                    } catch (InterruptedException ex) {
                    }
                }
            }

            Storage st = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                long last_send_instant = System.nanoTime();
                if (verbose) System.out.print(this.id + " // Sending req " + req + "...");

                if(readOnly)
                        proxy.invokeUnordered(request);
                else
                        proxy.invokeOrdered(request);
                        
                if (verbose) System.out.println(this.id + " // sent!");
                st.store(System.nanoTime() - last_send_instant);

                if (interval > 0) {
                    try {
                        //sleeps interval ms before sending next request
                        Thread.sleep(interval);
                    } catch (InterruptedException ex) {
                    }
                }
                                
                if (verbose && (req % 1000 == 0)) System.out.println(this.id + " // " + req + " operations sent!");
            }

            if(id == initId) {
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = " + st.getAverage(true) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + st.getDP(true) / 1000 + " us ");
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = " + st.getAverage(false) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
                System.out.println(this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + st.getMax(false) / 1000 + " us ");
            }
            
            proxy.close();
        }
    }
}
