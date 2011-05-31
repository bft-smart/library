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
package navigators.smart.tom.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;


import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.TOMSender;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Storage;

public class LatencyTestClient extends TOMSender {

    private Storage st;
    private int exec;
    private int argSize;
    private Semaphore sm = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);
    private int count = 0;
    private int f;
    private int n;
    private int currentId = 0;
    private long last_send_instant = 0;
    private int num_sends = 0;
    private int interval = 0;
    private boolean readOnly;

    public LatencyTestClient(int id, int exec, int argSize, int interval, boolean readOnly) {
        this.exec = exec;
        this.argSize = argSize;
        this.currentId = id;
        this.readOnly = readOnly;
        this.interval = interval;
        this.st = new Storage(exec / 2);

        //create the communication system
        this.init(id);



    }

    public void run() {
        this.f = getViewManager().getCurrentViewF();
        this.n = getViewManager().getCurrentViewN();
        while (true) {
            st.reset();
            long totalBegin = System.nanoTime();
            for (int i = 0; i < exec; i++) {
                try {
                    int currId = currentId;
                    num_sends = i;

                    byte[] command = new byte[4 + argSize];

                    ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                    try {
                        new DataOutputStream(out).writeInt(currId);
                    } catch (IOException ex) {
                        Logger.getLogger(LatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    System.arraycopy(out.toByteArray(), 0, command, 0, 4);

                    if (i % 1000 == 0) {
                        System.out.println("Sending " + (i + 1) + " / " + exec);
                    }

                    last_send_instant = System.nanoTime();
                    this.TOMulticast(command, generateRequestId(), 
                            ReconfigurationManager.TOM_NORMAL_REQUEST, readOnly);

                    this.sm.acquire();

                    if (interval > 0) {
                        //sleeps interval ms before sending next request
                        Thread.sleep(interval);
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            long totalElapsedTime = System.nanoTime() - totalBegin;

            System.out.println("Average time for " + exec / 2 + " executions (-10%) = " + this.st.getAverage(true) / 1000 + " us ");
            System.out.println("Standard desviation for " + exec / 2 + " executions (-10%) = " + this.st.getDP(true) / 1000 + " us ");
            System.out.println("Average time for " + exec / 2 + " executions (all samples) = " + this.st.getAverage(false) / 1000 + " us ");
            System.out.println("Standard desviation for " + exec / 2 + " executions (all samples) = " + this.st.getDP(false) / 1000 + " us ");
            System.out.println("Average time for " + exec + " executions using totalElapsedTime = " + (totalElapsedTime / exec) / 1000 + " us ");
            System.out.println("Maximum time for " + exec / 2 + " executions (-10%) = " + this.st.getMax(true) / 1000 + " us ");
            System.out.println("Maximum time for " + exec / 2 + " executions (all samples) = " + this.st.getMax(false) / 1000 + " us ");
        }
    }

    public void replyReceived(TOMMessage reply) {
        long receive_instant = System.nanoTime();

        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] response = reply.getContent();
        int id;
        try {
            id = new DataInputStream(new ByteArrayInputStream(response)).readInt();
        } catch (IOException ex) {
            Logger.getLogger(LatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        if (id == currentId) {
            count++;

            //contabiliza a latência quando recebe a f+1-esima resposta

            if (count == f + 1) {
                if (num_sends > exec / 2) {
                    this.st.store(receive_instant - last_send_instant);
                }

                count = 0;
                currentId += 1;
                this.sm.release();
            }
        }
        this.mutex.release();
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java LatencyTestClient <id> <number of messages> <argument size (bytes)> <interval between requests (ms)> <readOnly (default: false)>");
            System.exit(-1);
        }



        new LatencyTestClient(new Integer(args[0]), new Integer(args[1]),
                new Integer(args[2]), new Integer(args[3]),
                (args.length > 4) ? new Boolean(args[4]) : false).run();
    }
}
