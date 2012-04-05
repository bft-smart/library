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

package navigators.smart.tom.leaderchange;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import navigators.smart.paxosatwar.executionmanager.TimestampValuePair;

/**
 * This class represents a COLLECT object with the information about the running consensus
 *
 * @author Joao Sousa
 */
public class CollectData implements Externalizable {

    private int pid; // process id
    private int eid; // execution id
    private TimestampValuePair quorumWeaks; // last value recevied from a Byzantine quorum of WEAKS
    private HashSet<TimestampValuePair> writeSet; // values written by the replica
    
    /**
     * Empty constructor
     */
    public CollectData() {
        pid = -1;
        eid = -1;
        quorumWeaks = null;
        writeSet = null;
    }

    /**
     * Constructor
     *
     * @param pid process id
     * @param eid execution id
     * @param quorumWeaks last value recevied from a Byzantine quorum of WEAKS
     * @param writeSet values written by the replica
     */
    public CollectData(int pid, int eid, TimestampValuePair quorumWeaks, HashSet<TimestampValuePair> writeSet) {
        
        this.pid = pid;
        this.eid = eid;
        this.quorumWeaks = quorumWeaks;
        this.writeSet = writeSet;
    }

    /**
     * Get execution id
     * @return exection id
     */
    public int getEid() {
        return eid;
    }

    /**
     * Get process id
     * @return process id
     */
    public int getPid() {
        return pid;
    }

    /**
     * Get value received from a Byzantine quorum of WEAKS
     * @return value received from a Byzantine quorum of WEAKS
     */
    public TimestampValuePair getQuorumWeaks() {
        return quorumWeaks;
    }

    /**
     * Get set of values written by the replica
     * @return set of values written by the replica
     */
    public HashSet<TimestampValuePair> getWriteSet() {
        return writeSet;
    }

    public boolean equals(Object obj) {

        if (obj instanceof CollectData) {

            CollectData c = (CollectData) obj;

            if (c.pid == pid) return true;
        }

        return false;
    }

    public int hashCode() {
        return pid;
    }

    public void writeExternal(ObjectOutput out) throws IOException{

        out.writeInt(pid);
        out.writeInt(eid);
        out.writeObject(quorumWeaks);
        out.writeObject(writeSet);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{

        pid = in.readInt();
        eid = in.readInt();
        quorumWeaks = (TimestampValuePair) in.readObject();
        writeSet = (HashSet<TimestampValuePair>) in.readObject();
    }
}
