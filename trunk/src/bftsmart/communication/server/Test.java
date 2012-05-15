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

package bftsmart.communication.server;

import java.util.concurrent.LinkedBlockingQueue;

import bftsmart.communication.SystemMessage;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Storage;





public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

        //******* EDUARDO BEGIN **************//
        ServerViewManager manager = new ServerViewManager(Integer.parseInt(args[0]));
        LinkedBlockingQueue<SystemMessage> inQueue = new LinkedBlockingQueue<SystemMessage>();
        ServersCommunicationLayer scl = new ServersCommunicationLayer(manager, inQueue,null);

        int id = manager.getStaticConf().getProcessId();
        int n = manager.getCurrentViewN();
        //******* EDUARDO END **************//


        int[] targets = new int[n-1];

        System.out.println("n = "+n);

        for (int i=1; i<n; i++) {
            targets[i-1] = i;
        }

        int iteractions = Integer.parseInt(args[1]);

        int warmup = iteractions/2;
        int test = iteractions/2;

        for(int i=0; i<warmup; i++) {
            String msg = "m"+i;

            //System.out.println("sending "+msg);

            if(id == 0) {
                long time = System.nanoTime();

                scl.send(targets, new TOMMessage(id,0,i,msg.getBytes(),0));
                int rec = 0;

                while(rec < n-1) {
                    inQueue.take();
                    rec++;
                }

                //System.out.println();
                System.out.println("Roundtrip "+((System.nanoTime()-time)/1000.0)+" us");
            } else {
                TOMMessage m = (TOMMessage) inQueue.take();
                scl.send(new int[]{m.getSender()}, new TOMMessage(id,0,i,m.getContent(),0));
            }
        }

        System.out.println("Beginning the real test with "+test+" roundtrips");
        Storage st = new Storage(test);

        for(int i=0; i<test; i++) {
            String msg = "m"+i;
            if(id == 0) {
                long time = System.nanoTime();

                scl.send(targets, new TOMMessage(id,0,i,msg.getBytes(),0));
                int rec = 0;

                while(rec < n-1) {
                    inQueue.take();
                    rec++;
                }

                st.store(System.nanoTime()-time);
            } else {
                TOMMessage m = (TOMMessage) inQueue.take();
                scl.send(new int[]{m.getSender()}, new TOMMessage(id,0,i,m.getContent(),0));
            }
        }

        System.out.println("Average time for "+test+" executions (-10%) = "+st.getAverage(true)/1000+ " us ");
        System.out.println("Standard desviation for "+test+" executions (-10%) = "+st.getDP(true)/1000 + " us ");
        System.out.println("Maximum time for "+test+" executions (-10%) = "+st.getMax(true)/1000+ " us ");
        System.out.println("Average time for "+test+" executions (all samples) = "+st.getAverage(false)/1000+ " us ");
        System.out.println("Standard desviation for "+test+" executions (all samples) = "+st.getDP(false)/1000 + " us ");
        System.out.println("Maximum time for "+test+" executions (all samples) = "+st.getMax(false)/1000+ " us ");

        //scl.shutdown();
    }
}
