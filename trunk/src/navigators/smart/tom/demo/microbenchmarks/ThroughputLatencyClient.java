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

import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.util.Storage;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class ThroughputLatencyClient {

    public static int initId = 0;
    
    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 6) {
            System.out.println("Usage: ... ThroughputLatencyClient <num. threads> <process id> <number of operations> <request size> <interval> <read only?>");
            System.exit(-1);
        }

        int numThreads = Integer.parseInt(args[0]);
        initId = Integer.parseInt(args[1]);

        int numberOfOps = Integer.parseInt(args[2]);
        int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[4]);
        boolean readOnly = Boolean.parseBoolean(args[5]);

        Client[] c = new Client[numThreads];
        
        for(int i=0; i<numThreads; i++) {
            c[i] = new ThroughputLatencyClient.Client(initId+i,numberOfOps,requestSize,interval,readOnly);
            c[i].start();
        }
        
        try {
            c[0].join();
        } catch (InterruptedException ex) {
            ex.printStackTrace(System.err);
        }
        
        System.exit(0);
    }

    static class Client extends Thread {

        int id;
        int numberOfOps;
        int requestSize;
        int interval;
        boolean readOnly;
        
        public Client(int id, int numberOfOps, int requestSize, int interval, boolean readOnly) {
            super("Client "+id);
        
            this.id = id;
            this.numberOfOps = numberOfOps;
            this.requestSize = requestSize;
            this.interval = interval;
            this.readOnly = readOnly;
        }

        public void run() {
            ServiceProxy proxy = new ServiceProxy(id);
            //proxy.setInvokeTimeout(1);

            byte[] request = new byte[requestSize], reply;

            System.out.println("Warm up...");

            for (int i = 0; i < numberOfOps / 2; i++) {
                reply = proxy.invoke(request, readOnly);
            }

            Storage st = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++) {
                long last_send_instant = System.nanoTime();
                reply = proxy.invoke(request, readOnly);
                st.store(System.nanoTime() - last_send_instant);

                if (interval > 0) {
                    try {
                        //sleeps interval ms before sending next request
                        Thread.sleep(interval);
                    } catch (InterruptedException ex) {
                    }
                }
            }

            if(id == initId) {
                System.out.println("Average time for " + numberOfOps / 2 + " executions (-10%) = " + st.getAverage(true) / 1000 + " us ");
                System.out.println("Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + st.getDP(true) / 1000 + " us ");
                System.out.println("Average time for " + numberOfOps / 2 + " executions (all samples) = " + st.getAverage(false) / 1000 + " us ");
                System.out.println("Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
                System.out.println("Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + st.getMax(false) / 1000 + " us ");
            }
            
            proxy.close();
        }
    }
}
