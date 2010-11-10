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
import navigators.smart.tom.util.DebugInfo;

/**
 * This class implements a TOMReceiver, and also a replica for the server side of the application.
 * It receives requests from the clients, runs a TOM layer, and sends a reply back to the client
 * Applications must create a class that extends this one, and implement the executeCommand method
 *
 */
public abstract class ServiceReplica extends TOMReceiver implements Runnable {

    private int id; // replica ID
    private ServerCommunicationSystem cs = null; // Server side comunication system
    private BlockingQueue<TOMMessage> requestQueue; // Queue of messages received from the TOM layer
    private Thread replicaThread; // Thread that runs the replica code
    private ReconfigurationManager reconfManager;
    private boolean isToJoin = false;
    private ReentrantLock waitTTPJoinMsgLock = new ReentrantLock();
    private Condition canProceed = waitTTPJoinMsgLock.newCondition();
    private byte[] startState = null;

    /**
     * Constructor
     * @param id Replica ID
     */
    public ServiceReplica(int id) {
        /*this.id = id;
        this.reconfManager = new ReconfigurationManager(id);
        this.init();*/
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
        /*this.isInInitView = false;
        this.isToJoin = isToJoin;
        this.id = id;
        this.reconfManager = new ReconfigurationManager(id);
        this.init();*/
        this(id, "", isToJoin);
    }

    
    
    public ServiceReplica(int id, String configHome, boolean isToJoin) {
        //this.isInInitView = false;
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


                super.init(cs, this.reconfManager, r.getLastExecConsId(), r.getExecLeader()); // initiaze the TOM layer
                //this.startState = r.getStartState();

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


        // Initialize messages queue received from the TOM layer
        this.requestQueue = new LinkedBlockingQueue<TOMMessage>();

        this.replicaThread = new Thread(this);
        this.replicaThread.start(); // starts the replica
    }
    //******* EDUARDO END **************//

    /**
     * This method runs the replica code
     */
    @Override
    public void run() {
        //******* EDUARDO BEGIN **************//
        if (this.startState != null) {
            setState(this.startState);
            this.startState = null;
        }

        /**ISTO E CODIGO DO JOAO, PARA TENTAR RESOLVER UM BUG */
        cs.start();
        /******************************************************/
        
        //******* EDUARDO END **************//
        
        while (true) {
            TOMMessage msg = null;

            try {
                msg = requestQueue.take(); // Take a message received from the TOM layer
            } catch (InterruptedException ex) {
                continue;
            }
            msg.requestTotalLatency = System.currentTimeMillis() - msg.consensusStartTime;
            // Deliver the message to the application, and get the response

            byte[] response = executeCommand(msg.getSender(), msg.timestamp,
                    msg.nonces, msg.getContent(), msg.getDebugInfo());

            /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
            if (/*requestQueue.isEmpty() &&*/stateLock.tryLock()) {
                stateCondition.signal();
                stateLock.unlock();
            }
            /********************************************************/
            // send reply to the client
            cs.send(new int[]{msg.getSender()}, new TOMMessage(id, msg.getSession(),
                    msg.getSequence(), response, this.reconfManager.getCurrentViewId()));
        }
    }

    /**
     * This is the method invoked to deliver a totally ordered request.
     *
     * @param msg The request delivered by the TOM layer
     */
    @Override
    public void receiveOrderedMessage(TOMMessage msg) {
        requestQueue.add(msg);
    }

    /**
     * This is the method invoked to deliver a read-only request.
     *
     * @param msg The request delivered by the TOM layer
     */
    @Override
    public void receiveMessage(TOMMessage msg) {
        requestQueue.add(msg);
    }
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    private ReentrantLock stateLock = new ReentrantLock();
    private Condition stateCondition = stateLock.newCondition();

    @Override
    public byte[] getState() {
        stateLock.lock();
        while (!requestQueue.isEmpty()) {
            try {
                stateCondition.await();
            } catch (InterruptedException ex) {
                Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        byte[] state = serializeState();
        stateLock.unlock();
        return state;
    }

    protected abstract byte[] serializeState();

    @Override
    public void setState(byte[] state) {
        stateLock.lock();
        while (!requestQueue.isEmpty()) {
            try {
                stateCondition.await();
            } catch (InterruptedException ex) {
                Logger.getLogger(ServiceReplica.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        deserializeState(state);
        stateLock.unlock();
    }

    protected abstract void deserializeState(byte[] state);

    /********************************************************/
    /**
     * This method is where the application code is to be written. It is meant to be
     * implemented by subclasses of this class. The code for this method MUST use the value
     * of "timestamp" instead of relying on its own local clock, and "nonces" instead of trying
     * to generated its own random values. This is important because this values are the same for
     * all replicas, and therefore, ensure the determinism required in a replicated state machine.
     * It is crucial for the programmer to be aware of this.
     *
     * @param clientId The ID of the client that issue the request
     * @param timestamp A timestamp to be used by the application, in case it needs it
     * @param nonces Random values to be used by the application, in case it needs them
     * @param command The command issue by the client
     * @return the reply for the request issued by the client
     */
    public abstract byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info);
}
