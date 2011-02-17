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
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.TOMSender;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Storage;


public class ThroughputLatencyTestClient extends TOMSender implements Runnable {
    
    private Storage st;
    private int exec;
    private int argSize;
    private Semaphore sm = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);
    private int count = 0;
    private CommunicationSystemClientSide cs;
    private int f;
    private int n;
    private int myId;
    private int currentId = 0;
    private long last_send_instant = 0;
    private int num_sends = 0;
    private int interval = 0;
    private long initialNumOps[];
    private long initialTimestamp[];
    private long max;
    private int measurementEpoch;
          
    public ThroughputLatencyTestClient(int id, int exec, int argSize, int interval) {
        this.exec = exec;
        this.argSize = argSize;
        
        this.currentId = id;
        this.myId = id;


        this.interval = interval;
        this.st = new Storage(exec/2);  

        initialNumOps = new long[n];
        initialTimestamp = new long[n];

        for (int i=0; i<n; i++){
            initialNumOps[i]=0;
            initialTimestamp[i]=0;
        }
        max=0;
        measurementEpoch = 0;

        //create the communication system
        //cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf);
        this.init(this.myId);
        System.out.println("Cliente "+id+" lançado");
    }

    public void run(){
        this.f = getViewManager().getCurrentViewF();
        this.n = getViewManager().getCurrentViewN();
        try{
            System.out.println("(" + myId + ") A dormir 10 segundos ah espera das outras threads");
            Thread.sleep(10000);

            while(true){
                myId += exec;

                System.out.println("(" + myId + "-"+measurementEpoch+ ") Getting #ops from replicas before signing");

              //requests current number of ops processed by the servers
                byte[] command1 = new byte[4];
                ByteArrayOutputStream out1 = new ByteArrayOutputStream(4);
                try {
                    new DataOutputStream(out1).writeInt(-1);
                } catch (IOException ex) {
                    Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.arraycopy(out1.toByteArray(), 0, command1, 0, 4);
                currentId = -1;
                this.TOMulticast(command1);
                this.sm.acquire();

                measurementEpoch++;

               //generate exec signed messages
               System.out.println("Generating and signing "+exec+" messages");
                Hashtable generatedMsgs = new Hashtable();
                currentId=myId;
                int currId = currentId;
                for (int i=0; i<exec; i++){
                    byte[] command = new byte[4 + argSize];
                    ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                    try {
                        new DataOutputStream(out).writeInt(currId+i);
                    } catch (IOException ex) {
                        Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    System.arraycopy(out.toByteArray(), 0, command, 0, 4);
                    generatedMsgs.put(i, this.sign(command));
               }

               
               System.out.println("(" + myId + "-"+measurementEpoch+ ") Getting #ops from replicas after signing");
               
              //requests current number of ops processed by the servers
                command1 = new byte[4];
                out1 = new ByteArrayOutputStream(4);
                try {
                    new DataOutputStream(out1).writeInt(-1);
                } catch (IOException ex) {
                    Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.arraycopy(out1.toByteArray(), 0, command1, 0, 4);
                currentId = -1;
                this.TOMulticast(command1);
                this.sm.acquire();
                
                measurementEpoch++;

                currentId = myId;
                
              this.st.reset();
              long totalBegin = System.nanoTime();
              boolean firstTime = true;
              for (int i = 0; i < exec; i++) {
                try {                    
                    num_sends = i;                    
                    if (i % 1000 == 0) {
                        System.out.println("("+myId+"-"+measurementEpoch+") Sending " + (i + 1) + " / " + exec);
                    }
                    last_send_instant = System.nanoTime();
                    this.TOMulticast((TOMMessage)generatedMsgs.get(i));
                    
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
            System.out.println("--Resultados para cliente "+myId+" epoch "+measurementEpoch+ "-----------------------------------");
            System.out.println("(" + myId + "-"+measurementEpoch+")Average time for " + exec / 2 + " executions (-10%) = " + this.st.getAverage(true) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Standard desviation for " + exec / 2 + " executions (-10%) = " + this.st.getDP(true) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Average time for " + exec / 2 + " executions (all samples) = " + this.st.getAverage(false) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Standard desviation for " + exec / 2 + " executions (all samples) = " + this.st.getDP(false) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Average time for " + exec + " executions using totalElapsedTime = " + (totalElapsedTime / exec) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Maximum time for " + exec / 2 + " executions (-10%) = " + this.st.getMax(true) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")Maximum time for " + exec / 2 + " executions (all samples) = " + this.st.getMax(false) / 1000 + " us ");
            System.out.println("(" + myId + "-"+measurementEpoch+")----------------------------------------------------------------------");
          }
        } catch (InterruptedException ex) {
            Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void replyReceived(TOMMessage reply){

        long receive_instant = System.nanoTime();

        try{
            this.mutex.acquire();
        }catch(Exception e){
            e.printStackTrace();
        }

        byte[] response = reply.getContent();
        int id;
        long numOps=0;
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(response));
            id = dis.readInt();
            if (id==-1)
               numOps = dis.readLong();
        } catch (IOException ex) {
            Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        
        if(id == currentId){
            count++;

            //contabiliza a latência quando recebe a f+1-esima resposta

            if((id != -1) && count == f+1){
              if (num_sends>exec/2){
                this.st.store(receive_instant - last_send_instant);
              }

              count = 0;
              currentId+=1;
              this.sm.release();
            }
            else if (id==-1) {
                count ++;
                long opsSinceLastCount;
                long timeInterval;
                if (initialNumOps[reply.getSender()]!=0){
                    opsSinceLastCount = numOps - initialNumOps[reply.getSender()];
                    timeInterval = receive_instant - initialTimestamp[reply.getSender()];
                    double opsPerSec_ = ((double)opsSinceLastCount)/(timeInterval/1000000000.0);
                    long opsPerSec = Math.round(opsPerSec_);
                    if (opsPerSec>max)
                        max = opsPerSec;
                    System.out.println("(" + myId + "-"+measurementEpoch+")Time elapsed since epoch start: "+ (timeInterval/1000000000.0) + " seconds");
                    System.out.println("(" + myId + "-"+measurementEpoch+")Number of requestes finished since epoch start: "+ exec);
                    System.out.println("(" + myId + "-"+measurementEpoch+")Last "+opsSinceLastCount+" decisions were done at a rate of " + opsPerSec + " ops per second");
                    System.out.println("(" + myId + "-"+measurementEpoch+")Maximum throughput until now: " + max + " ops per second");
                }

                initialNumOps[reply.getSender()] = numOps;
                initialTimestamp[reply.getSender()] = receive_instant;

                if (count==n){
                    count = 0;
                    this.sm.release();
                }


            }
        }
        else{
            System.out.println("Discarding reply with id= "+id+" because currentId is "+currentId);
        }
        this.mutex.release();
    }

    public static void main(String[] args){
        if (args.length < 5){
            System.out.println("Usage: java ThroughputLatencyTestClient <num threads> <start id> <number of messages> <argument size (bytes)> <interval between requests (ms)>");
            System.exit(-1);
        }

        int numThreads = new Integer(args[0]);
        int startId = new Integer(args[1]);
        int numMsgs = new Integer(args[2]);
        int argSize = new Integer(args[3]);
        int interval = new Integer(args[4]);

        //TOMConfiguration conf = new TOMConfiguration(startId);
        Thread[] t = new Thread[numThreads];
        
        for (int i=0; i<numThreads; i++){
            //TOMConfiguration conf1 = new TOMConfiguration(conf,startId);
            //TOMConfiguration conf1 = new TOMConfiguration(startId);

            t[i] = new Thread(new ThroughputLatencyTestClient(startId, numMsgs,
                argSize, interval));
            t[i].start();

            startId++;
        }

         for (int i=0; i<numThreads; i++){
            try {
                t[i].join();
            } catch (InterruptedException ex) {
                Logger.getLogger(ThroughputLatencyTestClient.class.getName()).log(Level.SEVERE, null, ex);
            }
         }
    }
}
