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

package navigators.smart.tom;

//import br.ufsc.das.communication.SimpleCommunicationSystem;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class implements a TOMReplyReceiver, and a proxy to be used on the client side of the application.
 * It sends a request to the replicas, receives the reply, and delivers it to the
 * application
 *
 */
public class ServiceProxy extends TOMSender {

    private int n; // Number of total replicas in the system
    private int f; // Number of maximum faulty replicas assumed to occur

    // Semaphores used to render this object thread-safe
    // TODO: o mutex nao poderia ser antes um reentrantlock, em vez de um semaforo?
    private Semaphore mutex = new Semaphore(1);
    private Semaphore sm = new Semaphore(0);

    private int reqId = -1; // request id
    private TOMMessage replies[] = null; // Replies from replicas are stored here
    private byte[] response = null; // Pointer to the reply that is actually delivered to the application
 

    /**
     * Constructor
     * 
     * @param id Process id for this client
     */
    public ServiceProxy(int id) {
        TOMConfiguration conf = new TOMConfiguration(id);
        init(conf);
    }

    /**
     * Constructor
     * 
     * @param id Process id for this client
     * @param configHome Configuration directory for JBP
     * TODO: E mesmo a directoria do JBP, ou de alguma biblioteca de terceiros?
     */
    public ServiceProxy(int id, String configHome) {
        TOMConfiguration conf = new TOMConfiguration(id,configHome);
        init(conf);
    }

    // This method initializes the object
    private void init(TOMConfiguration conf) {
        init(CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf), conf);
        n = conf.getN();
        f = conf.getF();
        replies = new TOMMessage[n];
    }

    /**
     * This method sends a request to the replicas, and returns the related reply. This method is
     * thread-safe.
     *
     * @param request Request to be sent
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request) {
        return invoke(request,false);
    }

    /**
     * This method sends a request to the replicas, and returns the related reply. This method is
     * thread-safe.
     *
     * @param request Request to be sent
     * @param readOnly it is a read only request (will not be ordered)
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request, boolean readOnly) {

        // Ahead lies a critical section.
        // This ensures the thread-safety by means of a semaphore
        try{
            this.mutex.acquire();
        }catch(Exception e){
            e.printStackTrace();
        }

        // Discard previous replies
        Arrays.fill(replies, null);
        response = null;

        // Send the request to the replicas, and get its ID
        doTOMulticast(request,readOnly);
        reqId = getLastSequenceNumber();

        // Critical section ends here. The semaphore can be released
        this.mutex.release();

        // This instruction blocks the thread, until a response is obtained.
        // The thread will be unblocked when the method replyReceived is invoked
        // by the client side communication system
        try {
            this.sm.acquire();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        return response; // return the response
    }

    /**
     * This is the method invoked by the client side comunication system.
     *
     * @param reply The reply delivered by the client side comunication system
     */
    public void replyReceived(TOMMessage reply) {
        int sender = reply.getSender();
        if(sender >= n) { //ignore messages that don't come from replicas
            return;
        }

        // Ahead lies a critical section.
        // This ensures the thread-safety by means of a semaphore
        try{
            this.mutex.acquire();
        }catch(Exception e){
            e.printStackTrace();
        }

        if(reply.getSequence() == reqId) { // Is this a reply for the last request sent?
            replies[sender] = reply;

            // Compare the reply just received, to the others
            for(int i = 0; i<replies.length; i++) {
                if(replies[i] != null) {
                    int sameContent = 1;
                    byte[] content = replies[i].getContent();

                    for(int j = i+1; j<replies.length; j++) {
                        if(replies[j] != null && Arrays.equals(replies[j].getContent(),content)) {
                            sameContent++;

                            // if there are more than f equal replies, this cycle can end
                            if(sameContent >= f+1) {
                                break;
                            }
                        }
                    }

                    // Is there already more than f equal replies?
                    if (sameContent >= f + 1) {
                        response = content;
                        reqId = -1;
                        this.sm.release(); // unblocks the thread that is executing the "invoke" method,
                                           // so it can deliver the reply to the application
                        break;
                    }
                }
            }
        }

        // Critical section ends here. The semaphore can be released
        this.mutex.release();
    }
}
