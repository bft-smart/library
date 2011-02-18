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
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.TOMSender;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


public class ThroughputTestClient extends TOMSender implements Runnable {
    
    private int exec;
    private int argSize;
    private int interval;
    private int id = 0;
    private CommunicationSystemClientSide cs;
    private int f;
    private int n;
    private int currentId = 0;
    private Hashtable sessionTable;
    private TOMConfiguration conf;

        public ThroughputTestClient(int id, int exec, int argSize, int interval, TOMConfiguration conf)  {
        this.id = id;
        this.exec = exec;
        this.argSize = argSize;
        this.interval = interval;        
        //id for this client
        this.currentId = id;
        this.conf = conf;
        //create the configuration object
        
        //create the communication system

        cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf);
        //the number of suported faults
        this.f = conf.getF();
        this.n = conf.getN();
        //initialize the TOM sender
        this.init(cs, conf);
    }

    public void run() {
        LinkedList<TOMMessage> generatedMessages = null;
        try {
            System.out.println("(" + currentId + ") A dormir 10 segundos ah espera dos outros clientes");
            Thread.sleep(10000);
            /*
            if (conf.getUseSignatures()==1){
                System.out.println("Pre-generating signed requests ...");
                generatedMessages = new LinkedList();
                //pre-generate and sign the messages to be sent
                for (int i = 0; i < exec; i++) {
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
                for (int i = 0; i < exec; i++) {
              //      if (conf.getUseSignatures()==0){
                        int currId = currentId;
                        byte[] command = new byte[4 + argSize];
                        ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                        try {
                            new DataOutputStream(out).writeInt(-2);
                        } catch (IOException ex) {
                            java.util.logging.Logger.getLogger(LatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.arraycopy(out.toByteArray(), 0, command, 0, 4);
                        this.doTOMulticast(command);
                //    }
                //    else {
                //        this.doTOMulticast(generatedMessages.get(i));
                //    }
                    if ((i!=0) && ((i % 1000) == 0)) {
                        long elapsedTime = System.currentTimeMillis() - startTimeInstant;
                        double opsPerSec_ = ((double)1000)/(((double)elapsedTime/1000));
                        long opsPerSec = Math.round(opsPerSec_);
                        System.out.println(i + "/" + exec + " messages sent, elapsedTime="+elapsedTime+", throughput="+opsPerSec+" per sec");
                        startTimeInstant = System.currentTimeMillis();
                    }
                    //checks if the interval is greater or equal than the precision of the timer (1000 us = 1 ms)
                    if (interval >= 1000) {
                        try {
                            //sleeps interval/1000 ms before sending next request
                            Thread.sleep(interval/1000);
                            //Thread.sleep(interval);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    else if (interval!=0) {
                        //sleep 1 ms when a sufficient number of executions has been done
                        if ((i!=0) && (i%(1000/interval)==0)){
                            Thread.sleep(1);
                        }
                    }
                }
                System.out.println("Press Ctrl+C to stop");
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ex) {
                Logger.getLogger(ThroughputTestClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(ThroughputTestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java ThroughputTestClient <process id (>=1000)> <number of messages> <argument size (bytes)> <interval (us)> <numThreads>");
            System.exit(-1);
        }

        int id = new Integer(args[0]);
        if (id < 1000) {
            System.err.println("Error: process id needs to be greater or equal than 1000");
            System.exit(-11);
        }
        int numThreads = new Integer(args[4]);
        Thread[] t = new Thread[numThreads];
        TOMConfiguration conf = new TOMConfiguration(id);
        for (int i=0; i<numThreads; i++){
            TOMConfiguration conf1 = new TOMConfiguration(conf,1000*i+id);
            t[i] = new Thread(new ThroughputTestClient(id+i, new Integer(args[1]),
                new Integer(args[2]), new Integer(args[3]), conf1));
            t[i].start();
        }
        try {
            t[numThreads - 1].join();
        } catch (InterruptedException ex) {
            Logger.getLogger(ThroughputTestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void replyReceived(TOMMessage reply) {
        //throw new UnsupportedOperationException("Not used...");
    }
}