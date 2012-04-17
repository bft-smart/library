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

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.communication.client.ReplyReceiver;
import navigators.smart.reconfiguration.ClientViewManager;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.messages.TOMMessageType;



/**
 * This class is used to multicast messages to replicas and receive replies.
 */
public abstract class TOMSender implements ReplyReceiver {

    private int me; // process id

    //******* EDUARDO BEGIN **************//
    //private int[] group; // group of replicas
    private ClientViewManager viewManager;
    //******* EDUARDO END **************//
    
    private int session = 0; // session id
    private int sequence = 0; // sequence number
    private int unorderedMessageSequence = 0; // sequence number for readonly messages 
    private CommunicationSystemClientSide cs; // Client side comunication system
    private Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
    private boolean useSignatures = false;

    /**
     * Creates a new instance of TOMulticastSender
     *
     * TODO: This may really be empty?
     */
    public TOMSender() {
    }

    public void close(){
        cs.close();
    }

    public CommunicationSystemClientSide getCommunicationSystem() {
        return this.cs;
    }
    
    
    //******* EDUARDO BEGIN **************//
    public ClientViewManager getViewManager(){
        return this.viewManager;
    }

    /**
     * This method initializes the object
     * TODO: Ask if this method cannot be protected (compiles, but....)
     *
     * @param processId ID of the process
     */
    public void init(int processId) {
        this.viewManager = new ClientViewManager(processId);
        startsCS();
    }
    
    public void init(int processId, String configHome) {
        this.viewManager = new ClientViewManager(processId,configHome);
        startsCS();
    }

    private void startsCS() {
        this.cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(this.viewManager);
        this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
        this.me = this.viewManager.getStaticConf().getProcessId();
        this.useSignatures = this.viewManager.getStaticConf().getUseSignatures()==1?true:false;
        this.session = new Random().nextInt();
    }
    //******* EDUARDO END **************//
    
    
    public int getProcessId() {
        return me;
    }

    public int generateRequestId(TOMMessageType type) {
        lock.lock();
        int id;
        if(type == TOMMessageType.ORDERED_REQUEST)
            id = sequence++;
        else
            id = unorderedMessageSequence++; 
        lock.unlock();

        return id;
    }

    //******* EDUARDO BEGIN **************//
    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     */
    //public void TOMulticast(byte[] m) {
    //    TOMulticast(new TOMMessage(me, session, generateRequestId(), m,
    //            this.viewManager.getCurrentViewId()));
    //}

    /**
     * Multicast a TOMMessage to the group of replicas
     *
     * @param sm Message to be multicast
     */
    public void TOMulticast(TOMMessage sm) {
        cs.send(useSignatures, this.viewManager.getCurrentViewProcesses(), sm);
    }

    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     * @param reqId unique integer that identifies this request
     * @param reqType TOM_NORMAL, TOM_READONLY or TOM_RECONFIGURATION
     */
    public void TOMulticast(byte[] m, int reqId, TOMMessageType reqType) {
//        cs.send(useSignatures, viewManager.getCurrentViewProcesses(),
//                new TOMMessage(me, session, reqId, m, viewManager.getCurrentViewId(),
//                reqType));
        cs.send(useSignatures, new int[]{0, 1}, 
                new TOMMessage(me, session, reqId, m, viewManager.getCurrentViewId(),
                reqType));
    }
    
    public void sendMessageToTargets(byte[] m, int reqId, int[] targets) {
        cs.send(useSignatures, targets,
                new TOMMessage(me, session, reqId, m, viewManager.getCurrentViewId(), TOMMessageType.UNORDERED_REQUEST));
    }
    
    /**
     * Create TOMMessage and sign it
     *
     * @param m Data to be included in TOMMessage
     *
     * @return TOMMessage with serializedMsg and serializedMsgSignature fields filled
     */
    //public TOMMessage sign(byte[] m) {
    //    TOMMessage tm = new TOMMessage(me, session, generateRequestId(), m,
    //           this.viewManager.getCurrentViewId());
    //    cs.sign(tm);
    //    return tm;
    //}
    
    //******* EDUARDO END **************//
}
