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

/**
 * This class represents a proof sent by a replica to the leader for the consensus being started
 * 
 */
public class RTCollect implements Serializable{
    
    private int newLeader; // New leader for the next consensus being started
    private long lastConsensus; // Last consensus executed, or being executed
    private int reqId; // Request ID associated with the timeout
    
    /**
     * Creates a new instance of TimerRequestCollect
     * @param newLeader New leader for the next consensus being started
     * @param lastConsensus Last consensus executed, or being executed
     * @param reqId Request ID associated with the timeout
     */
    public RTCollect(int newLeader, long lastConsensus, int reqId) {
        this.newLeader = newLeader;
        this.lastConsensus = lastConsensus;
        this.reqId = reqId;
    }

    /**
     * Retrieves the new leader for the next consensus being started
     * @return The new leader for the next consensus being started
     */
    public int getNewLeader(){
        return this.newLeader;
    }

    /**
     * Retrieves the request ID associated with the timeout
     * @return The request ID associated with the timeout
     */
    public int getReqId(){
        return this.reqId;
    }

    /**
     * Retrieves the last consensus executed, or being executed
     * @return The last consensus executed, or being executed
     */
    public long getLastConsensus(){
        return this.lastConsensus;
    }
}
