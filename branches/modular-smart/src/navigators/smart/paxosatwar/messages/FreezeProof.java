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

package navigators.smart.paxosatwar.messages;

import navigators.smart.tom.util.SerialisationHelper;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 *
 * @author edualchieri
 *
 * Proofs for one (freezed) consensus.
 */
public final class FreezeProof {

    private int pid; // Replica ID
    private long eid; // Consensus's execution ID
    private int round; // Round number

    private byte[] weak; // weakly accepted value
    private byte[] strong; // strongly accepted value
    private byte[] decide; // decided value

    /**
     * Creates a new instance of FreezeProof
     * @param pid Replica ID
     * @param eid Consensus's execution ID
     * @param round Round number
     * @param weak Weakly accepted value
     * @param strong Strongly accepted Value
     * @param decide Decided value
     */
    public FreezeProof(int pid, long eid, int round,
            byte[] weak, byte[] strong, byte[] decide) {

        this.pid = pid;
        this.eid = eid;
        this.round = round;

        this.weak = weak;
        this.strong = strong;
        this.decide = decide;
    }

    /**
     * Retrieves the replica ID
     * @return Replica ID
     */
    public int getPid() {

        return pid;

    }

    /**
     * Retrieves the consensus's execution ID
     * @return Consensus's execution ID
     */
    public long getEid() {

        return eid;

    }

    /**
     * Retrieves the round number
     * @return Round number
     */
    public int getRound() {

        return round;

    }

    /**
     * Retrieves the weakly accepted value
     * @return Weakly accepted value
     */
    public byte[] getWeak() {

        return weak;

    }

    /**
     * Retrieves the strongly accepted value
     * @return Strongly accepted value
     */
    public byte[] getStrong() {

        return strong;

    }
    
    /**
     * Retrieves the decided value
     * @return Decided value
     */
    public byte[] getDecide() {

        return decide;

    }

    // Overwriten methods below
    
    @Override
    public String toString() {

        return "W="+str(weak)+" S="+str(strong)+" D="+str(decide);

    }

    private final String str(byte[] obj) {
        return (obj == null)?"*":new String(obj);
    }

    public FreezeProof(DataInput in) throws IOException{
        pid = in.readInt();
        eid = in.readLong();
        round = in.readInt();
        weak = SerialisationHelper.readByteArray(in);
        strong = SerialisationHelper.readByteArray(in);
        decide = SerialisationHelper.readByteArray(in);
    }

    public void serialize(DataOutput out) throws IOException{
        out.writeInt(pid);
        out.writeLong(eid);
        out.writeInt(round);
        SerialisationHelper.writeByteArray(weak, out);
        SerialisationHelper.writeByteArray(strong, out);
        SerialisationHelper.writeByteArray(decide, out);
    }
}

