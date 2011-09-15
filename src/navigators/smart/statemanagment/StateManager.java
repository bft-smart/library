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

package navigators.smart.statemanagment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.DeliveryThread;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.leaderchange.LCManager;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;

/**
 * TODO: Não sei se esta classe sera usada. Para já, deixo ficar
 *
 *  Verificar se as alterações para suportar dinamismo estão corretas
 * @author Joao Sousa
 */
public class StateManager {

    private StateLog log;
    private HashSet<SenderEid> senderEids = null;
    private HashSet<SenderState> senderStates = null;
    private HashSet<SenderView> senderViews = null;

    private ReentrantLock lockState = new ReentrantLock();
    private ReentrantLock lockTimer = new ReentrantLock();

    private Timer stateTimer = null;

    private int lastEid;
    private int waitingEid;
    private int replica;
    private byte[] state;

    private ReconfigurationManager reconfManager;
    private TOMLayer tomLayer;
    private DeliveryThread dt;
    private LCManager lcManager;
    
    public StateManager(ReconfigurationManager manager, TOMLayer tomLayer, DeliveryThread dt, LCManager lcManager) {

        //******* EDUARDO BEGIN **************//
        this.reconfManager = manager;
        int k = this.reconfManager.getStaticConf().getCheckpointPeriod();
        //******* EDUARDO END **************//

        this.tomLayer = tomLayer;
        this.dt = dt;
        this.lcManager = lcManager;

        this.log = new StateLog(k);
        senderEids = new HashSet<SenderEid>();
        senderStates = new HashSet<SenderState>();
        senderViews = new HashSet<SenderView>();

        this.replica = 0;

        if (replica == manager.getStaticConf().getProcessId()) changeReplica();
        this.state = null;
        this.lastEid = -1;
        this.waitingEid = -1;
    }
    
    public int getReplica() {
        return replica;
    }

    public void changeReplica() {

        //******* EDUARDO BEGIN **************//
        int pos = -1;
        do {
            //TODO: Verificar se continua correto
            pos = this.reconfManager.getCurrentViewPos(replica);
            replica = this.reconfManager.getCurrentViewProcesses()[(pos + 1) % reconfManager.getCurrentViewN()];

            //replica = (replica + 1) % manager.getCurrentViewN();
        //******* EDUARDO END **************//
        } while (replica == reconfManager.getStaticConf().getProcessId());
    }

    public void setReplicaState(byte[] state) {
        this.state = state;
    }

    public byte[] getReplicaState() {
        return state;
    }
    public void addEID(int sender, int eid) {
        senderEids.add(new SenderEid(sender, eid));
    }

    public void emptyEIDs() {
        senderEids.clear();
    }

    public void emptyEIDs(int eid) {
        for (SenderEid m : senderEids)
            if (m.eid <= eid) senderEids.remove(m);
    }

    public boolean moreThanF_EIDs(int eid) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderEid m : senderEids) {
            if (m.eid == eid && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > reconfManager.getCurrentViewF();
        //******* EDUARDO END **************//
    }

    public void addView(int sender, int view) {
        senderViews.add(new SenderView(sender, view));
    }

    public void emptyViews() {
        senderViews.clear();
    }

    public void emptyViews(int view) {
        for (SenderView m : senderViews)
            if (m.view <= view) senderViews.remove(m);
    }
    
    public boolean moreThan2F_Views(int view) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderView m : senderViews) {
            if (m.view == view && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > reconfManager.getQuorum2F();
        //******* EDUARDO END **************//
    }

    public void addState(int sender, TransferableState state) {
        senderStates.add(new SenderState(sender, state));
    }

    public void emptyStates() {
        senderStates.clear();
    }

    public int getWaiting() {
        return waitingEid;
    }

    public void setWaiting(int wait) {
        this.waitingEid = wait;
    }
    public void setLastEID(int eid) {
        lastEid = eid;
    }

    public int getLastEID() {
        return lastEid;
    }


    public boolean moreThanF_Replies() {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderState m : senderStates) {
            if (!replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > reconfManager.getCurrentViewF();
        //******* EDUARDO END **************//
    }

    public TransferableState getValidHash() {

        SenderState[] st = new SenderState[senderStates.size()];
        senderStates.toArray(st);
        int count = 0;

        for (int i = 0; i < st.length; i++) {

            for (int j = i; j < st.length; j++) {

                if (st[i].state.equals(st[j].state) && st[j].state.hasState()) count++;
                //******* EDUARDO BEGIN **************//
                if (count > reconfManager.getCurrentViewF()) return st[j].state;
                //******* EDUARDO END **************//
            }
        }

        return null;
    }

    public int getNumValidHashes() {

        SenderState[] st = new SenderState[senderStates.size()];
        senderStates.toArray(st);
        int count = 0;

        for (int i = 0; i < st.length; i++) {

            for (int j = i; j < st.length; j++) {

                if (st[i].state.equals(st[j].state) && st[j].state.hasState()) count++;
 
            }
        }

        return count;
    }

    public int getReplies() {
        return senderStates.size();
    }

    public StateLog getLog() {
        return log;
    }

    public void saveState(byte[] state, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        lockState.lock();

        Logger.println("(TOMLayer.saveState) Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.newCheckpoint(state, tomLayer.computeHash(state));
        thisLog.setLastEid(-1);
        thisLog.setLastCheckpointEid(lastEid);
        thisLog.setLastCheckpointRound(decisionRound);
        thisLog.setLastCheckpointLeader(leader);

        lockState.unlock();

        Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void saveBatch(byte[] batch, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        lockState.lock();

        Logger.println("(TOMLayer.saveBatch) Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.addMessageBatch(batch, decisionRound, leader);
        thisLog.setLastEid(lastEid);

        lockState.unlock();

        Logger.println("(TOMLayer.saveBatch) Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void requestState(int sender, int eid) {

            Logger.println("(TOMLayer.requestState) The state transfer protocol is enabled");

            if (getWaiting() == -1) {

                Logger.println("(TOMLayer.requestState) I'm not waiting for any state, so I will keep record of this message");
                addEID(sender, eid);

                if (getLastEID() < eid && moreThanF_EIDs(eid)) {

                    Logger.println("(TOMLayer.requestState) I have now more than " + reconfManager.getCurrentViewF() + " messages for EID " + eid + " which are beyond EID " + getLastEID());

                    if (tomLayer.requestsTimer != null) tomLayer.requestsTimer.clearAll();
                    setLastEID(eid);
                    setWaiting(eid - 1);
                    //stateManager.emptyReplicas(eid);// isto causa uma excepcao

                    SMMessage smsg = new SMMessage(reconfManager.getStaticConf().getProcessId(),
                            eid - 1, TOMUtil.SM_REQUEST, getReplica(), null, -1);
                    tomLayer.getCommunication().send(reconfManager.getCurrentViewOtherAcceptors(), smsg);

                    Logger.println("(TOMLayer.requestState) I just sent a request to the other replicas for the state up to EID " + (eid - 1));

                    TimerTask stateTask =  new TimerTask() {
                        public void run() {

                        lockTimer.lock();

                        Logger.println("(TimerTask.run) Timeout for the replica that was supposed to send the complete state. Changing desired replica.");
                        System.out.println("Timeout no timer do estado!");

                        setWaiting(-1);
                        changeReplica();
                        emptyStates();
                        setReplicaState(null);

                        lockTimer.unlock();
                        }
                    };

                    Timer stateTimer = new Timer("state timer");
                    stateTimer.schedule(stateTask,1500);
                }
            }

        /************************* TESTE *************************
        System.out.println("[/TOMLayer.requestState]");
        /************************* TESTE *************************/
    }

    public void SMRequestDeliver(SMMessage msg) {

        //******* EDUARDO BEGIN **************//
        if (reconfManager.getStaticConf().isStateTransferEnabled()) {
        //******* EDUARDO END **************//

            Logger.println("(TOMLayer.SMRequestDeliver) The state transfer protocol is enabled");

            lockState.lock();

            Logger.println("(TOMLayer.SMRequestDeliver) I received a state request for EID " + msg.getEid() + " from replica " + msg.getSender());

            boolean sendState = msg.getReplica() == reconfManager.getStaticConf().getProcessId();
            if (sendState) Logger.println("(TOMLayer.SMRequestDeliver) I should be the one sending the state");

            TransferableState thisState = getLog().getTransferableState(msg.getEid(), sendState);

            lockState.unlock();

            if (thisState == null) {
                Logger.println("(TOMLayer.SMRequestDeliver) I don't have the state requested :-(");

              thisState = new TransferableState();
            }

            int[] targets = { msg.getSender() };
            SMMessage smsg = new SMMessage(reconfManager.getStaticConf().getProcessId(),
                    msg.getEid(), TOMUtil.SM_REPLY, -1, thisState, lcManager.getLastts());

            // malicious code, to force the replica not to send the state
            //if (reconfManager.getStaticConf().getProcessId() != 0 || !sendState)
            tomLayer.getCommunication().send(targets, smsg);

            Logger.println("(TOMLayer.SMRequestDeliver) I sent the state for checkpoint " + thisState.getLastCheckpointEid() + " with batches until EID " + thisState.getLastEid());

        }
    }

    public void SMReplyDeliver(SMMessage msg) {

        //******* EDUARDO BEGIN **************//

        lockTimer.lock();
        if (reconfManager.getStaticConf().isStateTransferEnabled()) {
        //******* EDUARDO END **************//

            Logger.println("(TOMLayer.SMReplyDeliver) The state transfer protocol is enabled");
            Logger.println("(TOMLayer.SMReplyDeliver) I received a state reply for EID " + msg.getEid() + " from replica " + msg.getSender());

            if (getWaiting() != -1 && msg.getEid() == getWaiting()) {

                int currentView = -1;
                addView(msg.getSender(), msg.getView());
                if (moreThan2F_Views(msg.getView())) currentView = msg.getView();

                Logger.println("(TOMLayer.SMReplyDeliver) The reply is for the EID that I want!");

                if (msg.getSender() == getReplica() && msg.getState().getState() != null) {
                    Logger.println("(TOMLayer.SMReplyDeliver) I received the state, from the replica that I was expecting");
                    setReplicaState(msg.getState().getState());
                    if (stateTimer != null) stateTimer.cancel();
                }

                addState(msg.getSender(),msg.getState());

                if (moreThanF_Replies()) {

                    Logger.println("(TOMLayer.SMReplyDeliver) I have at least " + reconfManager.getCurrentViewF() + " replies!");

                    TransferableState state = getValidHash();

                    int haveState = 0;
                    if (getReplicaState() != null) {
                        byte[] hash = null;
                        hash = tomLayer.computeHash(getReplicaState());
                        if (state != null) {
                            if (Arrays.equals(hash, state.getStateHash())) haveState = 1;
                            else if (getNumValidHashes() > reconfManager.getCurrentViewF()) haveState = -1;

                        }
                    }

                    if (state != null && haveState == 1 && currentView > -1) {

                        lcManager.setLastts(currentView);
                        lcManager.setLastts(currentView);
                        tomLayer.lm.setNewTS(currentView);

                        Logger.println("(TOMLayer.SMReplyDeliver) The state of those replies is good!");

                        state.setState(getReplicaState());

                        lockState.lock();

                        getLog().update(state);

                        lockState.unlock();

                        dt.deliverLock();

                        //ot.OutOfContextLock();

                        setWaiting(-1);

                        dt.update(state);
                        tomLayer.processOutOfContext();

                        dt.canDeliver();

                        //ot.OutOfContextUnlock();
                        dt.deliverUnlock();

                        emptyStates();
                        setReplicaState(null);

                        System.out.println("Actualizei o estado!");

                    //******* EDUARDO BEGIN **************//
                    } else if (state == null && (reconfManager.getCurrentViewN() / 2) < getReplies()) {
                    //******* EDUARDO END **************//

                        Logger.println("(TOMLayer.SMReplyDeliver) I have more than " +
                                (reconfManager.getCurrentViewN() / 2) + " messages that are no good!");

                        setWaiting(-1);
                        emptyStates();
                        setReplicaState(null);

                        if (stateTimer != null) stateTimer.cancel();
                    } else if (haveState == -1) {

                        Logger.println("(TOMLayer.SMReplyDeliver) The replica from which I expected the state, sent one which doesn't match the hash of the others, or it never sent it at all");

                        setWaiting(-1);
                        changeReplica();
                        emptyStates();
                        setReplicaState(null);

                        if (stateTimer != null) stateTimer.cancel();
                    }
                }
            }
        }
        lockTimer.unlock();
    }

    private class SenderView {

        private int sender;
        private int view;

        SenderView(int sender, int view) {
            this.sender = sender;
            this.view = view;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderEid) {
                SenderEid m = (SenderEid) obj;
                return (m.eid == this.view && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.view;
            return hash;
        }
    }

    private class SenderEid {

        private int sender;
        private int eid;

        SenderEid(int sender, int eid) {
            this.sender = sender;
            this.eid = eid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderEid) {
                SenderEid m = (SenderEid) obj;
                return (m.eid == this.eid && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.eid;
            return hash;
        }
    }
    
    private class SenderState {

        private int sender;
        private TransferableState state;

        SenderState(int sender, TransferableState state) {
            this.sender = sender;
            this.state = state;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderState) {
                SenderState m = (SenderState) obj;
                return (this.state.equals(m.state) && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender;
            hash = hash * 31 + this.state.hashCode();
            return hash;
        }
    }
}
