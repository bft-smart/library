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

    private byte[][] messageBatches; // batches received since the last checkpoint.
    private int lastCheckpointEid; // Execution ID for the last checkpoint
    private byte[] state; // State associated with the last checkpoint
    private int lastEid = -1; // Execution ID for the last messages batch delivered to the application
    private boolean hasState; // indicates if the TransferableState object has a valid state

    /**
     * Constructs a TansferableState
     * This constructor should be used when there is a valid state to construct the object with
     * @param messageBatches Batches received since the last checkpoint.
     * @param nextEid Execution ID for the last checkpoint
     * @param state State associated with the last checkpoint
     */
    public TransferableState(byte[][] messageBatches, int lastCheckpointEid, int lastEid, byte[] state) {

        this.messageBatches = messageBatches; // batches received since the last checkpoint.
        this.lastCheckpointEid = lastCheckpointEid; // Execution ID for the last checkpoint
        this.lastEid = lastEid; // Execution ID for the last messages batch delivered to the application
        this.state = state; // State associated with the last checkpoint
        this.hasState = true;
    }

    /**
     * Constructs a TansferableState
     * This constructor should be used when there isn't a valid state to construct the object with
     */
    public TransferableState() {
        this.messageBatches = null; // batches received since the last checkpoint.
        this.lastCheckpointEid = -1; // Execution ID for the last checkpoint
        this.lastEid = -1;
        this.state = null; // State associated with the last checkpoint
        this.hasState = false;
    }

    /**
     * Indicates if the TransferableState object has a valid state
     * @return true if it has a valid state, false otherwise
     */
    public boolean hasState() {
        return hasState;
    }

    /**
     * Retrieves all batches of messages
     * @return Batch of messages
     */
    public byte[][] getMessageBatches() {
        return messageBatches;
    }

    /**
     * Retrieves the specified batch of messages
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public byte[] getMessageBatch(int eid) {
        if (eid >= lastCheckpointEid && eid <= lastEid) {
            return messageBatches[eid - lastCheckpointEid - 1];
        }
        else return null;
    }

    /**
     * Retrieves the execution ID for the last checkpoint
     * @return Execution ID for the last checkpoint, or -1 if no checkpoint was yet executed
     */
    public int getLastCheckpointEid() {

        return lastCheckpointEid;
    }

    /**
     * Retrieves the execution ID for the last messages batch delivered to the application
     * @return Execution ID for the last messages batch delivered to the application
     */
    public int getLastEid() {

        return lastEid;
    }
    
    /**
     * Retrieves the state associated with the last checkpoint
     * @return State associated with the last checkpoint
     */
    public byte[] getState() {
        return state;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TransferableState) {
            TransferableState tState = (TransferableState) obj;

            if (this.messageBatches == null && tState.messageBatches == null) return true;

            if ((this.messageBatches != null && tState.messageBatches == null) ||
                    (this.messageBatches == null && tState.messageBatches != null)) return false;

            if (this.messageBatches != null && tState.messageBatches != null &&
                    this.messageBatches.length != tState.messageBatches.length) return false;
                 
            for (int i = 0; i < this.messageBatches.length; i++)
                if (!Arrays.equals(this.messageBatches[i], tState.messageBatches[i])) return false;
            
            return (Arrays.equals(this.state, tState.state) &&
                    tState.lastCheckpointEid == this.lastCheckpointEid && tState.lastEid == this.lastEid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + this.lastCheckpointEid;
        hash = hash * 31 + this.lastEid;
        if (this.state != null)
            for (int i = 0; i < this.state.length; i++) hash = hash * 31 + (int) this.state[i];
        else hash = hash * 31 + 0;
        if (this.messageBatches != null)
            for (int i = 0; i < this.messageBatches.length; i++)
                if (this.messageBatches[i] != null)
                    for (int j = 0; j < this.messageBatches[i].length; j++)
                        hash = hash * 31 + (int) this.messageBatches[i][j];
        else hash = hash * 31 + 0;
        return hash;
    }
}
