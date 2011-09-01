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
import java.util.Hashtable;
import navigators.smart.reconfiguration.ReconfigurationManager;

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

    //******* EDUARDO BEGIN: estas variaveis devem ser acessadas a partir da classe ReconfigurationManager **************//
    //private int f;
    //private int n;
    //private int me;
    //******* EDUARDO END **************//


    private int lastEid;
    private int waitingEid;
    private int replica;
    private byte[] state;

    private ReconfigurationManager manager;

    public StateManager(ReconfigurationManager manager) {

        //******* EDUARDO BEGIN **************//
        this.manager = manager;
        int k = this.manager.getStaticConf().getCheckpointPeriod();
        //******* EDUARDO END **************//

        this.log = new StateLog(k);
        senderEids = new HashSet<SenderEid>();
        senderStates = new HashSet<SenderState>();
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
            pos = this.manager.getCurrentViewPos(replica);
            replica = this.manager.getCurrentViewProcesses()[(pos + 1) % manager.getCurrentViewN()];

            //replica = (replica + 1) % manager.getCurrentViewN();
        //******* EDUARDO END **************//
        } while (replica == manager.getStaticConf().getProcessId());
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

    public boolean moreThenF_EIDs(int eid) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderEid m : senderEids) {
            if (m.eid == eid && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        //******* EDUARDO BEGIN **************//
        return count > manager.getCurrentViewF();
        //******* EDUARDO END **************//
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
        return count > manager.getCurrentViewF();
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
                if (count > manager.getCurrentViewF()) return st[j].state;
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
