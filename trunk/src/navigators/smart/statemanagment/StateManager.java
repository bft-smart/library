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

import java.util.HashSet;
import java.util.Hashtable;

/**
 * TODO: Não sei se esta classe sera usada. Para já, deixo ficar
 * 
 * @author Jo�o Sousa
 */
public class StateManager {

    private StateLog log;
    private HashSet<Message> messages = null;
    private int f;
    private int lastEid;

    public StateManager(int k, int f) {

        this.log = new StateLog(k);
        messages = new HashSet<Message>();
        this.f = f;
        this.lastEid = -1;
    }

    public void addReplica(int sender, int eid) {
        messages.add(new Message(sender, eid));
    }

    public void emptyReplicas() {
        messages.clear();
    }

    public void emptyReplicas(int eid) {
        for (Message m : messages)
            if (m.eid <= eid) messages.remove(m);
    }
    
    public void setLastEID(int eid) {
        lastEid = eid;
    }

    public int getLastEID() {
        return lastEid;
    }

    public boolean moreThenF(int eid) {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (Message m : messages) {
            if (m.eid == eid && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        return count > f;
    }

    public StateLog getLog() {
        return log;
    }

    private class Message {

        private int sender;
        private int eid;

        Message(int sender, int eid) {
            this.sender = sender;
            this.eid = eid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Message) {
                Message m = (Message) obj;
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
}
