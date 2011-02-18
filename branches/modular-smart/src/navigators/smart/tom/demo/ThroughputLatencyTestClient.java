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
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.TOMSender;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Storage;
import navigators.smart.tom.util.TOMConfiguration;


public class ThroughputLatencyTestClient extends TOMSender implements Runnable {
    
    private Storage st;
    private int exec;
    private int argSize;
    private Semaphore sm = new Semaphore(1);
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
    /** Receiver or the messages */
    private int target;
    /** Shall the client multi or unicast the message*/
    private boolean multicast;
          
    public ThroughputLatencyTestClient(int id, int exec, int argSize, int interval, TOMConfiguration conf, boolean multicast) {
    	this.multicast = multicast;
        this.exec = exec;
        this.argSize = argSize;
        this.target = id%conf.getN();
        this.currentId = id;
        this.myId = id;

        this.f = conf.getF();
        this.n = conf.getN();
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
        
        try {
			sm.acquire(); //burn acquire so that thread waits for release
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 

        //create the communication system
        cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf);
        this.init(cs, conf);
        System.out.println("Cliente "+id+" launched");
    }

    public void run(){
        try{
        	int sleeptime = 1000;
            System.out.println("(" + myId + ") Sleeping " + sleeptime/1000 +" seconds waiting for other threads!");
            Thread.sleep(sleeptime);

            while(true){
//                myId += exec;

                System.out.println("(" + myId + "-"+measurementEpoch+ ") Getting #ops from replicas before signing");
              //requests current number of ops processed by the servers
                ByteBuffer command1 = ByteBuffer.allocate(4);
                command1.putInt(-1);
                currentId = -1;
                
                if(multicast)
                	this.doTOMulticast(command1.array());
                else 
                	doTOUnicast(target,createTOMMsg(command1.array()));
                
                this.sm.acquire();	//wait for reply
                
                //create msg for # of ops request after signing (id has to be taken before
                TOMMessage msg = createTOMMsg(command1.array());

                measurementEpoch++;

               //generate exec signed messages
               System.out.println(myId+": Generating and signing "+exec+" messages");
                Hashtable<Integer,TOMMessage> generatedMsgs = new Hashtable<Integer,TOMMessage>();
                currentId=myId;
                int currId = currentId;
                for (int i=0; i<exec; i++){
                    ByteBuffer buf = ByteBuffer.allocate(4 + argSize);
                    buf.putInt(currId + i);
                    generatedMsgs.put(i, this.createTOMMsg(buf.array()));
               }

               
               System.out.println("(" + myId + "-"+measurementEpoch+ ") Getting #ops from replicas after signing");
               
               	//requests current number of ops processed by the servers
                currentId = -1;
                
                if(multicast)
                	this.TOMulticast(msg);
                else
                	doTOUnicast(target,msg);
                
                this.sm.acquire();
                
                measurementEpoch++;

                currentId = myId;
                
              this.st.reset();
              long totalBegin = System.nanoTime();
              for (int i = 0; i < exec; i++) {
                try {                    
                    num_sends = i;                    
                    if (i % 1000 == 0) {
                        System.out.println("("+myId+"-"+measurementEpoch+") Sending " + (i + 1) + " / " + exec);
                    }
                    last_send_instant = System.nanoTime();
                    
                    if(multicast)
                    	this.TOMulticast(generatedMsgs.get(i));
                    else
                    	this.doTOUnicast(target,generatedMsgs.get(i));
                    
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
            System.out.println("--Results for client "+myId+" epoch "+measurementEpoch+ "-----------------------------------");
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
        } catch (Exception e){
        	e.printStackTrace();
        }
    }

    public void replyReceived(TOMMessage reply){
    	
//    	System.out.println("(" + myId + "-"+measurementEpoch+ ") Received Reply");

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

			if ((id != -1) && count == f + 1) {
				if (num_sends > exec / 2) {
					this.st.storeDuration(receive_instant - last_send_instant);
				}

				count = 0;
				currentId += 1;
				this.sm.release();
			} else if (id == -1) {
				long opsSinceLastCount;
				long timeInterval;
				if (initialNumOps[reply.getSender()] != 0) {
					opsSinceLastCount = numOps - initialNumOps[reply.getSender()];
					timeInterval = receive_instant - initialTimestamp[reply.getSender()];
					double opsPerSec_ = opsSinceLastCount / (timeInterval / 1000000000.0);
					long opsPerSec = Math.round(opsPerSec_);
					if (opsPerSec > max)
						max = opsPerSec;
					System.out.println("Reply #ops from "+reply.getSender());
					System.out.println("(" + myId + "-" + measurementEpoch + ")Time elapsed since epoch start: "
							+ (timeInterval / 1000000000.0) + " seconds");
					System.out.println("(" + myId + "-" + measurementEpoch + ")Number of requestes finished since epoch start: " + exec);
					System.out.println("(" + myId + "-" + measurementEpoch + ")Last " + opsSinceLastCount
							+ " decisions were done at a rate of " + opsPerSec + " ops per second");
					System.out.println("(" + myId + "-" + measurementEpoch + ")Maximum throughput until now: " + max + " ops per second");
				}

				initialNumOps[reply.getSender()] = numOps;
				initialTimestamp[reply.getSender()] = receive_instant;

				if (count > f+1) {
					count = 0;
					this.sm.release();
				}

			}
        }else{
//            System.out.println(myId +": Discarding reply with id= "+id+" because currentId is "+currentId);
        }
        this.mutex.release();
    }

    public static void main(String[] args){
        if (args.length < 6){
            System.out.println("Usage: java ThroughputLatencyTestClient <num threads> <start id> <number of messages> <argument size (bytes)> <interval between requests (ms)> <multicast to all replicas: (true/false)>");
            System.exit(-1);
        }

        int numThreads = new Integer(args[0]);
        int startId = new Integer(args[1]);
        int numMsgs = new Integer(args[2]);
        int argSize = new Integer(args[3]);
        int interval = new Integer(args[4]);
        boolean multicast = Boolean.parseBoolean(args[5]);

        Thread[] t = new Thread[numThreads];
        
        for (int i=0; i<numThreads; i++){
            TOMConfiguration conf1 = new TOMConfiguration(startId);

            t[i] = new Thread(new ThroughputLatencyTestClient(startId, numMsgs,
                argSize, interval, conf1,multicast));
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
