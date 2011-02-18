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

import java.io.Serializable;
import java.util.Arrays;

/**
 * This classe represents a state tranfered from a replica to another. The state associated with the last
 * checkpoint together with all the batches of messages received do far, comprises the sender's
 * current state
 * 
 * @author Jo�o Sousa
 */
public class TransferableState implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 8541571983747254820L;
	public final BatchInfo[] messageBatches; // batches received since the last checkpoint.
    public final long lastCheckpointEid; // Execution ID for the last checkpoint
    public final int lastCheckpointRound; // Round for the last checkpoint
    public final int lastCheckpointLeader; // Leader for the last checkpoint
    public final byte[] state; // State associated with the last checkpoint
    public final byte[] stateHash; // Hash of the state associated with the last checkpoint
    public final long lastEid; // Execution ID for the last messages batch delivered to the application
    public final boolean hasState; // indicates if the TransferableState object has a valid state
    public final byte[] leadermodulestate;
    
    /**
     * Constructs a TansferableState
     * This constructor should be used when there is a valid state to construct the object with
     * @param lastCheckpointEid Execution ID for the last checkpoint
     * @param lastCheckpointRound The round in which the last execution of this checkpoint was decided
     * @param lastEid The last ExecutionId that was executed within this checkpoint
     * @param state State associated with the last checkpoint
     * @param stateHash Hash of the state associated with the last checkpoint
     * @param leaderModulestate Serialized version of the current state of the leader module to be sent to the slow replica
     * @param messageBatches the Batches logged after the state was stored
     */
    public TransferableState( long lastCheckpointEid, int lastCheckpointRound, int lastCheckpointLeader, long lastEid, byte[] state, byte[] stateHash, byte[] leadermodulestate, BatchInfo[] messageBatches) {
        this.lastCheckpointEid = lastCheckpointEid; // Execution ID for the last checkpoint
        this.lastCheckpointRound = lastCheckpointRound; // Round for the last checkpoint
        this.lastCheckpointLeader = lastCheckpointLeader; // Leader for the last checkpoint
        this.lastEid = lastEid; // Execution ID for the last messages batch delivered to the application
        this.state = state; // State associated with the last checkpoint
        this.stateHash = stateHash;
        this.hasState = true;
        this.leadermodulestate = leadermodulestate; //state of the leadermodule
        this.messageBatches = messageBatches;
    }

    /**
     * Constructs a TansferableState
     * This constructor should be used when there isn't a valid state to construct the object with
     */
    public TransferableState() {
        this.messageBatches = null; // batches received since the last checkpoint.
        this.lastCheckpointEid = -1; // Execution ID for the last checkpoint
        this.lastCheckpointRound = -1; // Round for the last checkpoint
        this.lastCheckpointLeader = -1; // Leader for the last checkpoint
        this.lastEid = -1;
        this.state = null; // State associated with the last checkpoint
        this.stateHash = null;
        this.hasState = false;
        this.leadermodulestate = null;
    }

    /**
     * Retrieves the specified batch of messages
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public BatchInfo getMessageBatch(long eid) {
        if (eid >= lastCheckpointEid && eid <= lastEid) {
            return messageBatches[(int)(eid - lastCheckpointEid - 1)];
        }
        else {
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TransferableState) {
            TransferableState tState = (TransferableState) obj;

            if ((this.messageBatches != null && tState.messageBatches == null) ||
                    (this.messageBatches == null && tState.messageBatches != null)) return false;

            if (this.messageBatches != null && tState.messageBatches != null) {

                if (this.messageBatches.length != tState.messageBatches.length) return false;
                 
                for (int i = 0; i < this.messageBatches.length; i++)
                    if (!this.messageBatches[i].equals(tState.messageBatches[i])) return false;
            }
            return (Arrays.equals(this.stateHash, tState.stateHash) &&
                    tState.lastCheckpointEid == this.lastCheckpointEid &&
                    tState.lastCheckpointRound == this.lastCheckpointRound &&
                    tState.lastCheckpointLeader == this.lastCheckpointLeader &&
                    tState.lastEid == this.lastEid && tState.hasState == this.hasState);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = (int) (hash * 31 + this.lastCheckpointEid);
        hash = hash * 31 + this.lastCheckpointRound;
        hash = hash * 31 + this.lastCheckpointLeader;
        hash = (int) (hash * 31 + this.lastEid);
        hash = hash * 31 + (this.hasState ? 1 : 0);
        if (this.stateHash != null) {
            for (int i = 0; i < this.stateHash.length; i++) hash = hash * 31 + this.stateHash[i];
        } else {
            hash = hash * 31 + 0;
        }
        if (this.messageBatches != null) {
            for (int i = 0; i < this.messageBatches.length; i++) {
                if (this.messageBatches[i] != null) {
                    hash = hash * 31 + this.messageBatches[i].hashCode();
                } else {
                    hash = hash * 31 + 0;
                }
            }
        } else {
            hash = hash * 31 + 0;
        }
        return hash;
    }
}
