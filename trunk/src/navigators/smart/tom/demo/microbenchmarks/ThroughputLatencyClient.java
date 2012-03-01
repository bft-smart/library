/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.tom.demo.microbenchmarks;

import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.core.messages.TOMMessageType;
import navigators.smart.tom.util.Storage;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ThroughputLatencyClient {

    public static int initId = 0;
    
    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 8) {
            System.out.println("Usage: ... ThroughputLatencyClient <num. threads> <process id> <number of operations> <request size> <interval> <read only?> <verbose?> <DoS?>");
            System.exit(-1);
        }

        int numThreads = Integer.parseInt(args[0]);
        initId = Integer.parseInt(args[1]);

        int numberOfOps = Integer.parseInt(args[2]);
        int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[4]);
        boolean readOnly = Boolean.parseBoolean(args[5]);
        boolean verbose = Boolean.parseBoolean(args[6]);
        boolean dos = Boolean.parseBoolean(args[7]);

        Client[] c = new Client[numThreads];
        
        for(int i=0; i<numThreads; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(ThroughputLatencyClient.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            System.out.println("Launching client " + (initId+i));
            c[i] = new ThroughputLatencyClient.Client(initId+i,numberOfOps,requestSize,interval,readOnly, verbose, dos);
            //c[i].start();
        }

        for(int i=0; i<numThreads; i++) {

            
            c[i].start();
        }
        
        
        for(int i=0; i<numThreads; i++) {

            try {
                c[i].join();
            } catch (InterruptedException ex) {
                ex.printStackTrace(System.err);
            }
        }

        
        System.exit(0);
    }

    static class Client extends Thread {

        int id;
        int numberOfOps;
        int requestSize;
        int interval;
        boolean readOnly;
        boolean verbose;
        boolean dos;
        ServiceProxy proxy;
        byte[] request;
        
        public Client(int id, int numberOfOps, int requestSize, int interval, boolean readOnly, boolean verbose, boolean dos) {
            super("Client "+id);
        
            this.id = id;
            this.numberOfOps = numberOfOps;
            this.requestSize = requestSize;
            this.interval = interval;
            this.readOnly = readOnly;
            this.verbose = verbose;
            this.proxy = new ServiceProxy(id);
            this.request = new byte[this.requestSize];
            this.dos = dos;
        }

        public void run() {
            //ServiceProxy proxy = new ServiceProxy(id);
            //proxy.setInvokeTimeout(1);

            byte[] reply;
            int reqId;

            System.out.println("Warm up...");

            int req = 0;
            
            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                if (verbose) System.out.print("Sending req " + req + "...");
                if (dos) {
                    //proxy.asycInvoke(request);
                    reqId = proxy.generateRequestId();
                    proxy.TOMulticast(request, reqId, (readOnly) ? TOMMessageType.UNORDERED_REQUEST : TOMMessageType.ORDERED_REQUEST); 
                }
                else
                	if(readOnly)
                		reply = proxy.invokeUnordered(request);
                	else
                		reply = proxy.invokeOrdered(request);
                if (verbose) System.out.println(" sent!");

                if (verbose && (req % 1000 == 0)) System.out.println(this.id + " // " + req + " operations sent!");
            }

            Storage st = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                long last_send_instant = System.nanoTime();
                if (verbose) System.out.print(this.id + " // Sending req " + req + "...");
                if (dos) {
                    //proxy.asycInvoke(request);
                    reqId = proxy.generateRequestId();
                    proxy.TOMulticast(request, reqId, (readOnly) ? TOMMessageType.UNORDERED_REQUEST : TOMMessageType.ORDERED_REQUEST); 

                }
                else
                	if(readOnly)
                		reply = proxy.invokeUnordered(request);
                	else
                		reply = proxy.invokeOrdered(request);
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
            
            //proxy.close();
        }
    }
}
