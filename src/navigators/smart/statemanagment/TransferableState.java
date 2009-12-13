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

/**
 * This classe represents a state tranfered from a replica to another. The state associated with the last
 * checkpoint together with all the batches of messages received do far, comprises the sender's
 * current state
 * 
 * @author João Sousa
 */
public class TransferableState {

    private byte[][] messageBatches; // batches received since the last checkpoint.
    private int nextEid; // Execution ID for the last checkpoint
    private byte[] state; // State associated with the last checkpoint

    /**
     * Constructs a TansferableState
     * @param messageBatches Batches received since the last checkpoint.
     * @param nextEid Execution ID for the last checkpoint
     * @param state State associated with the last checkpoint
     */
    public TransferableState(byte[][] messageBatches, int nextEid, byte[] state) {

        this.messageBatches = messageBatches; // batches received since the last checkpoint.
        this.nextEid = nextEid; // Execution ID for the last checkpoint
        this.state = state; // State associated with the last checkpoint
        
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
        if (eid >= nextEid && eid < (nextEid + messageBatches.length)) {
            return messageBatches[eid - nextEid];
        }
        else return null;
    }

    /**
     * Retrieves the execution ID for the last checkpoint
     * @return Execution ID for the last checkpoint, or -1 if no checkpoint was yet executed
     */
    public int getCurrentCheckpointEid() {

        return nextEid - 1;
    }
    
    /**
     * Retrieves the state associated with the last checkpoint
     * @return State associated with the last checkpoint
     */
    public byte[] getState() {
        return state;
    }
}
