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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.reconfiguration.Reconfiguration;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.reconfiguration.ReconfigureReply;
import navigators.smart.reconfiguration.TTPMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.messages.TOMMessageType;

/**
 * This class implements a TOMReceiver, and also a replica for the server side of the application.
 * It receives requests from the clients, runs a TOM layer, and sends a reply back to the client
 * Applications must create a class that extends this one, and implement the executeOrdered method
 *
 */
public abstract class ServiceReplica extends TOMReceiver implements Runnable {

    class MessageContextPair {
        TOMMessage message;
        MessageContext msgCtx;
        
        MessageContextPair(TOMMessage message, MessageContext msgCtx) {
            this.message = message;
            this.msgCtx = msgCtx;
        }
    }
    
    // replica ID
    private int id; 
    // Server side comunication system
    private ServerCommunicationSystem cs = null; 
     // Queue of messages received from the TOM layer
    private BlockingQueue<MessageContextPair> requestQueue = new LinkedBlockingQueue<MessageContextPair>();
    // Thread that runs the replica code
    private Thread replicaThread; 
    
    private boolean active = false;
    
    private ReconfigurationManager reconfManager;
    private boolean isToJoin = false;
    private ReentrantLock waitTTPJoinMsgLock = new ReentrantLock();
    private Condition canProceed = waitTTPJoinMsgLock.newCondition();
    
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    private ReentrantLock requestsLock = new ReentrantLock();
    private Condition requestsCondition = requestsLock.newCondition();
    private boolean isQueueEmpty = true;
    /*******************************************************/

    /**
     * Constructor
     * @param id Replica ID
     */
    public ServiceReplica(int id) {
        this(id, "");
    }

    /**
     * Constructor
     * 
     * @param id Process ID
     * @param configHome Configuration directory for JBP
     */
    public ServiceReplica(int id, String configHome) {
        this.id = id;
        this.reconfManager = new ReconfigurationManager(id, configHome);
        this.init();
    }

    //******* EDUARDO BEGIN **************//
    
    /**
     * Constructor
     * @param id Replica ID
     * @param isToJoin: if true, the replica tries to join the system, otherwise it waits for TTP message
     * informing its join
     */
    public ServiceReplica(int id, boolean isToJoin) {
        this(id, "", isToJoin);
    }

    
    
    public ServiceReplica(int id, String configHome, boolean isToJoin) {
        this.isToJoin = isToJoin;
        this.id = id;
        this.reconfManager = new ReconfigurationManager(id, configHome);
        
        this.init();
    }

    //******* EDUARDO END **************//
    
    // this method initializes the object
    private void init() {

        try {
            cs = new ServerCommunicationSystem(this.reconfManager, this);
        } catch (Exception ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to build a communication system.");
        }

        //******* EDUARDO BEGIN **************//
        
        if (!this.reconfManager.isInInitView()) {
            if (this.isToJoin) {
                //Não está na visão inicial e é para executar um join;
                int port = this.reconfManager.getStaticConf().getServerToServerPort(id) - 1;
                String ip = this.reconfManager.getStaticConf().getServerToServerRemoteAddress(id).getAddress().getHostAddress();
                ReconfigureReply r = null;
                Reconfiguration rec = new Reconfiguration(id);
                do {
                    //System.out.println("while 1");
                    rec.addServer(id, ip, port);

                    r = rec.execute();
                } while (!r.getView().isMember(id));
                rec.close();
                this.reconfManager.processJoinResult(r);

                // initiaze the TOM layer
                super.init(cs, this.reconfManager, r.getLastExecConsId(), r.getExecLeader()); 

                this.cs.updateServersConnections();
                this.cs.joinViewReceived();
            } else {
                //Não está na visão inicial e é apenas para aguardar pela view onde o join foi executado
                waitTTPJoinMsgLock.lock();
                canProceed.awaitUninterruptibly();
                waitTTPJoinMsgLock.unlock();
            }

        } else {
            super.init(cs, this.reconfManager); // initiaze the TOM layer
        }
        initReplica();
    }

    public void joinMsgReceived(TTPMessage msg) {
        ReconfigureReply r = msg.getReply();
        
        if(r.getView().isMember(id)){
            this.reconfManager.processJoinResult(r);
            super.init(cs, this.reconfManager, r.getLastExecConsId(), r.getExecLeader()); // initiaze the TOM layer
            //this.startState = r.getStartState();
            cs.updateServersConnections();
            this.cs.joinViewReceived();
  
             waitTTPJoinMsgLock.lock();
             canProceed.signalAll();
             waitTTPJoinMsgLock.unlock();
        }
    }


    private void initReplica() {
        active = true;
        cs.start(); 
        
        replicaThread = new Thread(this);
        replicaThread.start(); // starts the replica
    }
    //******* EDUARDO END **************//

    /**
     * This method runs the replica code
     */
    @Override
    public void run() {
        MessageContextPair msg = null;
        byte[] response = null;
            
        while (active) {         
            // Take a message received from the TOMLayer
            try {
                msg = requestQueue.take(); 
            } catch (InterruptedException ex) {
                continue;
            }

            msg.msgCtx.getFirstInBatch().executedTime = System.nanoTime();
        
            // Deliver the message to the application, and get the response
            response = (msg.msgCtx.getConsensusId() == -1) ? 
                    executeUnordered(msg.message.getContent(), msg.msgCtx):
                    executeOrdered(msg.message.getContent(), msg.msgCtx);

            // If the request queue is empty, notify the logging code
            requestsLock.lock();
            if (requestQueue.isEmpty() || onlyUnorderedPending()) {
                isQueueEmpty = true;
                requestsCondition.signalAll();
            }
            requestsLock.unlock();
            
            // build the reply and send it to the client
            msg.message.reply = new TOMMessage(id, msg.message.getSession(),
                    msg.message.getSequence(), response, reconfManager.getCurrentViewId());            
            cs.send(new int[]{msg.message.getSender()}, msg.message.reply);
        }
    }

    private boolean onlyUnorderedPending() {
        for(MessageContextPair msg:requestQueue){
            if(msg.msgCtx.getConsensusId() != -1) {
                return false; //some message have consId != -1
            }
        }
        
        return true; //all messages have consId = -1
    }
    
    /**
     * This is the method invoked to deliver a totally ordered request.
     *
     * @param msg The request delivered by the delivery thread
     */
    @Override
    public final void receiveOrderedMessage(TOMMessage msg, MessageContext msgCtx) {
        /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
        isQueueEmpty = false;
        /*******************************************************/
        requestQueue.add(new MessageContextPair(msg,msgCtx));
    }

    /**
     * This is the method invoked to deliver a unordered (read-only) requests.
     * These requests are enqueued for processing just like ordered requests.
     *
     * @param msg the request delivered by the TOM layer
     */
    @Override
    public final void receiveMessage(TOMMessage msg, MessageContext msgCtx) {
        receiveOrderedMessage(msg, msgCtx);
    }

    /**
     * This method makes the replica leave the group
     */
    public void leave() {
        ReconfigureReply r = null;
        Reconfiguration rec = new Reconfiguration(id);
        do {
            //System.out.println("while 1");
            rec.removeServer(id);

            r = rec.execute();
        } while (r.getView().isMember(id));
        rec.close();
         this.cs.updateServersConnections();
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */

    @Override
    public byte[] getState() { //TODO: Ha por aqui uma condicao de corrida!
        requestsLock.lock();

        while (!isQueueEmpty) {
            try {
                requestsCondition.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        byte[] state = serializeState();
        requestsLock.unlock();
        return state;

    }

    @Override
    public void setState(byte[] state) {
        requestsLock.lock();
        while (!isQueueEmpty) {
            try {
                requestsCondition.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        deserializeState(state);
        requestsLock.unlock();
    }

    @Override
    public void waitForProcessingRequests() {
        requestsLock.lock();
        while (!isQueueEmpty) {
            try {
                requestsCondition.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        requestsLock.unlock();
    }

    protected abstract byte[] serializeState();

    protected abstract void deserializeState(byte[] state);

    /********************************************************/
    
    /**
     * Method called to execute a request totally ordered. It is meant to be
     * implemented by subclasses of this class. 
     * 
     * The message context contains a lot of information about the request, such
     * as timestamp, nonces and sender. The code for this method MUST use the value
     * of timestamp instead of relying on its own local clock, and nonces instead
     * of trying to generated its own random values.
     * 
     * This is important because this values are the same for all replicas, and
     * therefore, ensure the determinism required in a replicated state machine.
     *
     * @param command the command issue by the client
     * @param MessageContext msgCtx information related with the command
     * 
     * @return the reply for the request issued by the client
     */
    public abstract byte[] executeOrdered(byte[] command, MessageContext msgCtx);
    
    /**
     * Method called to execute a request totally ordered. It is meant to be
     * implemented by subclasses of this class. 
     * 
     * The message context contains some useful information such as the command
     * sender.
     * 
     * @param command the command issue by the client
     * @param MessageContext msgCtx information related with the command
     * 
     * @return the reply for the request issued by the client
     */
    public abstract byte[] executeUnordered(byte[] command, MessageContext msgCtx);
}
