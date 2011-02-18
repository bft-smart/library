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

package navigators.smart.tom.core.timer.messages;

import java.io.Serializable;
import java.security.SignedObject;

/**
 * This class serves as placeholder for the proofs of a RT leader change
 * 
 */

public class RTLeaderChange implements Serializable {

    public SignedObject[] proof; // Proofs for the new leader
    public int newLeader; // Replica ID of the new leader
    public long start; // ID of the consensus to be started

    /**
     * Creates a new instance of RTLeaderChangeMessage
     * @param proof Proofs for the new leader
     * @param nl Replica ID of the new leader
     * @param start ID of the consensus to be started
     */
    public RTLeaderChange(SignedObject[] proof, int nl, long start) {
        this.proof = proof;
        this.newLeader = nl;
        this.start = start;
    }

    /**
     * Checks if the new leader for the consensus being started is valid
     * according to an array of RTCollect proofs
     *
     * @param collect RTCoolect proofs that can confirm (or not) that newLeader is valid for the consensus being started
     * @param f MAximum number of faulty replicas that can exist
     * @return True if newLeader is valid, false otherwise
     */
    public boolean isAGoodStartLeader(RTCollect[] collect, int f) {
        int c = 0;
        for (int i = 0; i < collect.length; i++) {
            if (collect[i] != null && collect[i].getNewLeader() == newLeader) {
                c++;
            }
        }

        if (c <= f) {
            return false;
        }
        //there are at least f+1 collect messages that indicate newLeader as the new leader

        c = 0;
        for (int i = 0; i < collect.length; i++) {
            if (collect[i] != null && (start-1) <= collect[i].getLastConsensus()) {
                c++;
            }
        }

        //returns true if the number of processes that last executed start-1 is greater than f
        return (c > f);
    }
}