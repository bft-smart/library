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

/**
 * Dados sobre o ultimo consenso decidido
 *
 * @author Joao Sousa
 */
public class LastEidData implements Externalizable {

    private int pid; // id do processo
    private int eid; // id da execucao
    private byte[] eidDecision; // valor decidido
    private byte[] eidProof; // prova de decisao

    /**
     * Constructor vazio
     */
    public LastEidData() {
        pid = -1;
        eid = -1;
        eidDecision = null;
        eidProof = null;
    }

    /**
     * Cosntructor
     * 
     * @param pid id do processo
     * @param eid id da execucao
     * @param eidDecision valor decidido
     * @param eidProof prova de decisao
     */
    public LastEidData(int pid, int eid, byte[] eidDecision, byte[] eidProof) {

        this.pid = pid;
        this.eid = eid;
        this.eidDecision = eidDecision;
        this.eidProof = eidProof;
    }

    /**
     * Obter id da execucao
     * @return id da execucao
     */
    public int getEid() {
        return eid;
    }

    /**
     * Obter valor decidido
     * @return valor decidido
     */
    public byte[] getEidDecision() {
        return eidDecision;
    }

    /**
     * Obter prova de decisao
     * @return prova de decisao
     */
    public byte[] getEidProof() {
        return eidProof;
    }

    /**
     * Obter id do processo
     * @return id do processo
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
        eidProof = (byte[]) in.readObject();
    }
}
