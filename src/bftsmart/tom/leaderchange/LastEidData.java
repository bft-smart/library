/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.tom.leaderchange;

import bftsmart.paxosatwar.messages.PaxosMessage;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

/**
 * Data about the last consensus decision
 *
 * @author Joao Sousa
 */
public class LastEidData implements Externalizable {

    private int pid; // process id
    private int eid; // execution id
    private byte[] eidDecision; // decision value
    private Set<PaxosMessage>  eidProof; // proof of the decision
    
    /**
     * Empty constructor
     */
    public LastEidData() {
        pid = -1;
        eid = -1;
        eidDecision = null;
        eidProof = null;
    }

    /**
     * Constructor
     * 
     * @param pid process id
     * @param eid execution id
     * @param eidDecision decision value
     * @param eidProof proof of the decision
     */
    public LastEidData(int pid, int eid, byte[] eidDecision, Set<PaxosMessage> eidProof) {

        this.pid = pid;
        this.eid = eid;
        this.eidDecision = eidDecision;
        this.eidProof = eidProof;
    }

    /**
     * Get execution id
     * @return execution id
     */
    public int getEid() {
        return eid;
    }

    /**
     * Get decision value
     * @return decision value
     */
    public byte[] getEidDecision() {
        return eidDecision;
    }

    /**
     * Get proof of the decision
     * @return proof of the decision
     */
    public Set<PaxosMessage>  getEidProof() {
        return eidProof;
    }

    /**
     * Get process id
     * @return process id
     */
    public int getPid() {
        return pid;
    }
    public boolean equals(Object obj) {

        if (obj instanceof LastEidData) {

            LastEidData l = (LastEidData) obj;

            if (l.pid == pid) return true;
        }

        return false;
    }

    public int hashCode() {
        return pid;
    }
    
    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(pid);
        out.writeInt(eid);
        out.writeObject(eidDecision);
        out.writeObject(eidProof);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

        pid = in.readInt();
        eid = in.readInt();
        eidDecision = (byte[]) in.readObject();
        eidProof = (Set<PaxosMessage>) in.readObject();
    }
}
