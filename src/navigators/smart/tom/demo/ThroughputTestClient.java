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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.TOMSender;
import navigators.smart.tom.core.messages.TOMMessage;

public class ThroughputTestClient extends TOMSender implements Runnable {

    private int burst;
    private int argSize;
    private int interval;
    private int id = 0;
    private int f;
    private int n;
    private int currentId = 0;

    public ThroughputTestClient(int id, int burst, int argSize, int interval) {
        this.id = id;
        this.burst = burst;
        this.argSize = argSize;
        this.interval = interval;
        this.currentId = id;

        //initialize the TOM sender
        this.init(id);
    }

    @Override
    public void run() {
        this.f = getViewManager().getCurrentViewF();
        this.n = getViewManager().getCurrentViewN();

        IntervalSleeper sl = new IntervalSleeper(interval);

        ArrayList<TOMMessage> generatedMessages = null;

        try {
            System.out.println("(" + currentId + ") Let's sleep 10 seconds to wait for (possible) other clients");
            Thread.sleep(10000);

            //BBB start
            /*
            
            if (/**conf.getUseSignatures()1==1){ //assinaturas ligadas
            System.out.println("Pre-generating signed requests ...");
            generatedMessages = new ArrayList();
            //pre-generate and sign the messages to be sent
            for (int i = 0; i < /**exec100; i++) { //BBB end
            byte[] command = new byte[4 + argSize];
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            try {
            new DataOutputStream(out).writeInt(-2);
            } catch (IOException ex) {
            java.util.logging.Logger.getLogger(LatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.arraycopy(out.toByteArray(), 0, command, 0, 4);
            generatedMessages.add(this.sign(command));
            }
            }
             */
            try {
                System.out.println("Sending requests ...");
                long startTimeInstant = System.currentTimeMillis();

                int count = 0;
                long currMsg = 0;
                
                while (true) {
                    for (int i = 0; i < burst; i++) {
                        int currId = currentId;
                        //BBB start
                        byte[] command = new byte[4 + argSize];
                        ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                        try {
                            new DataOutputStream(out).writeInt(-2);
                        } catch (IOException ex) { ex.printStackTrace(System.err); }
                        System.arraycopy(out.toByteArray(), 0, command, 0, 4);
                        this.TOMulticast(/**generatedMessages.get((int) (currMsg % 100))**/
                                command);
                        currMsg++;

                        //BBB start
                        count += burst;
                        //BBB end

                        //BBB start
                        //todos os count eram i
                        //(opsPerSec/5) era opsPerSec
                        if ((count != 0) && ((count % 1000) == 0)) {
                            long elapsedTime = System.currentTimeMillis() - startTimeInstant;
                            double opsPerSec_ = ((double) 1000) / (((double) elapsedTime / 1000));
                            long opsPerSec = Math.round(opsPerSec_);
                            System.out.println(count + " messages sent, elapsedTime=" + elapsedTime + ", throughput=" + (opsPerSec / 5) + " per sec");
                            startTimeInstant = System.currentTimeMillis();
                        }
                    }
                    sl.delay(1); //1 x 'interval'ms
                }

            } catch (InterruptedException ex) {
                ex.printStackTrace(System.err);
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace(System.err);
        }
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage:... ThroughputTestClient <initial process id (>1000)> <num. msgs in a burst> <msg size (bytes)> <burst interval> <num threads>");
            System.exit(-1);
        }

        int id = new Integer(args[0]);
        if (id <= 1000) {
            System.err.println("Error: process id needs to be greater than 1000");
            System.exit(-11);
        }
        
        int numThreads = new Integer(args[4]);
        Thread[] t = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            t[i] = new Thread(new ThroughputTestClient(id + i, new Integer(args[1]),
                    new Integer(args[2]), new Integer(args[3])));
            t[i].start();
        }
        
        try {
            t[numThreads - 1].join();
        } catch (InterruptedException ex) {
            Logger.getLogger(ThroughputTestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void replyReceived(TOMMessage reply) {
        //throw new UnsupportedOperationException("Not used...");
    }

    /**
    
    Classe para o relogio.
    
    @author Marcelo Pasin
    
     **/
    private class IntervalSleeper extends TimerTask {

        private Timer timer;
        long interval, lastick;
        boolean waiting = true;

        /**
         * Create an interval timer. This timer starts at creation time and ticks at
         * every interval. Calls to sleep() wait until next tick.
         * @param interval
         */
        public IntervalSleeper(int interval) {
            timer = new Timer();
            timer.schedule(this, interval, interval);
            this.interval = interval;
            lastick = System.currentTimeMillis();
        }

        @Override
        public void run() {
            synchronized (this) {
                waiting = false;
                notify();
            }
        }

        /**
         * This method waits for the next clock tick.
         * @return the number of intervals elapsed since last call (or creation)
         * @throws InterruptedException
         */
        public int sleep() throws InterruptedException {
            synchronized (this) {
                while (waiting) {
                    wait(0);
                }
                waiting = true;
            }
            long end = System.currentTimeMillis();
            int ret = (int) ((end - lastick) / interval);
            lastick = lastick + interval * ret;
            return ret;
        }

        public void delay(int howMany/**, int tick**/
                ) throws InterruptedException {
            //int end = Integer.parseInt(args[0]);
            //IntervalSleeper sl = new IntervalSleeper(tick);

            for (int i = 0; i < howMany; i++) {
                int n = this.sleep();
                //System.out.println("tick... " + System.currentTimeMillis() + " n=" + n);
            }
        }
    }
}