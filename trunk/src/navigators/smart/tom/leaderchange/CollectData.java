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
import navigators.smart.paxosatwar.executionmanager.RoundValuePair;

/**
 * Esta classe representa um objecto COLLECT com as informacoes do consenso a decorrer
 *
 * @author Joao Sousa
 */
public class CollectData implements Externalizable {

    private int pid; // id do processo
    private int eid; // id da execucao
    private RoundValuePair quorumWeaks; // ultimo valor recebido de um quorum bizantino de WEAKS
    private HashSet<RoundValuePair> writeSet; // valores escritos pela replica

    /**
     * Constructor vazio
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
     * @param pid id do processo
     * @param eid id da execucao
     * @param quorumWeaks ultimo valor recebido de um quorum bizantino de WEAKS
     * @param writeSet valores escritos pela replica
     * @param signature assinatura da replica
     */
    public CollectData(int pid, int eid, RoundValuePair quorumWeaks, HashSet<RoundValuePair> writeSet) {
        
        this.pid = pid;
        this.eid = eid;
        this.quorumWeaks = quorumWeaks;
        this.writeSet = writeSet;
    }

    /**
     * Obter id da execucao
     * @return id da execucao
     */
    public int getEid() {
        return eid;
    }

    /**
     * Obter id do processo
     * @return id do processo
     */
    public int getPid() {
        return pid;
    }

    /**
     * Obter valor recebido de um quorum bizantino de WEAKS
     * @return valor recebido de um quorum bizantino de WEAKS
     */
    public RoundValuePair getQuorumWeaks() {
        return quorumWeaks;
    }

    /**
     * Obter conjunto de valores escritos pela replica
     * @return conjunto de valore escritos pela replica
     */
    public HashSet<RoundValuePair> getWriteSet() {
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
        quorumWeaks = (RoundValuePair) in.readObject();
        writeSet = (HashSet<RoundValuePair>) in.readObject();
    }
}
