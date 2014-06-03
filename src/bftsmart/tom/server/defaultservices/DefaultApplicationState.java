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
package bftsmart.tom.server.defaultservices;

import java.util.Arrays;

import bftsmart.tom.server.defaultservices.CommandsInfo;

import bftsmart.statemanagement.ApplicationState;

/**
 * This classe represents a state tranfered from a replica to another. The state associated with the last
 * checkpoint together with all the batches of messages received do far, comprises the sender's
 * current state
 * 
 * @author Jo�o Sousa
 */
public class DefaultApplicationState implements ApplicationState {

	private static final long serialVersionUID = 6771081456095596363L;

	protected byte[] state; // State associated with the last checkpoint
    protected byte[] stateHash; // Hash of the state associated with the last checkpoint
    protected int lastEid = -1; // Execution ID for the last messages batch delivered to the application
    protected boolean hasState; // indicates if the replica really had the requested state

    private CommandsInfo[] messageBatches; // batches received since the last checkpoint.
    private int lastCheckpointEid; // Execution ID for the last checkpoint
    private int lastCheckpointRound; // Round for the last checkpoint
    private int lastCheckpointLeader; // Leader for the last checkpoint
    private byte[] logHash;

    /**
     * Constructs a TansferableState
     * This constructor should be used when there is a valid state to construct the object with
     * @param messageBatches Batches received since the last checkpoint.
     * @param state State associated with the last checkpoint
     * @param stateHash Hash of the state associated with the last checkpoint
     */
    public DefaultApplicationState(CommandsInfo[] messageBatches, int lastCheckpointEid, int lastCheckpointRound, int lastCheckpointLeader, int lastEid, byte[] state, byte[] stateHash) {
       
        this.messageBatches = messageBatches; // batches received since the last checkpoint.
        this.lastCheckpointEid = lastCheckpointEid; // Execution ID for the last checkpoint
        this.lastCheckpointRound = lastCheckpointRound; // Round for the last checkpoint
        this.lastCheckpointLeader = lastCheckpointLeader; // Leader for the last checkpoint
        this.lastEid = lastEid; // Execution ID for the last messages batch delivered to the application
        this.state = state; // State associated with the last checkpoint
        this.stateHash = stateHash;
        this.hasState = true;
    }

    public DefaultApplicationState(CommandsInfo[] messageBatches, byte[] logHash, int lastCheckpointEid, int lastCheckpointRound, int lastCheckpointLeader, int lastEid, byte[] state, byte[] stateHash) {
    	this(messageBatches, lastCheckpointEid, lastCheckpointRound, lastCheckpointLeader, lastEid, state, stateHash);
    	this.logHash = logHash;
    }

    /**
     * Constructs a TansferableState
     * This constructor should be used when there isn't a valid state to construct the object with
     */
    public DefaultApplicationState() {
        this.messageBatches = null; // batches received since the last checkpoint.
        this.lastCheckpointEid = -1; // Execution ID for the last checkpoint
        this.lastCheckpointRound = -1; // Round for the last checkpoint
        this.lastCheckpointLeader = -1; // Leader for the last checkpoint
        this.lastEid = -1;
        this.state = null; // State associated with the last checkpoint
        this.stateHash = null;
        this.hasState = false;
    }
    
    
    public void setSerializedState(byte[] state) {
        this.state = state;
    }

    public byte[] getSerializedState() {
        return state;
    }
      
    /**
     * Indicates if the TransferableState object has a valid state
     * @return true if it has a valid state, false otherwise
     */
    public boolean hasState() {
        return hasState;
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

    /**
     * Retrieves the hash of the state associated with the last checkpoint
     * @return Hash of the state associated with the last checkpoint
     */
    public byte[] getStateHash() {
        return stateHash;
    }

    /**
     * Sets the state associated with the last checkpoint
     * @param state State associated with the last checkpoint
     */
    public void setState(byte[] state) {
        this.state = state;
    }
    
    /**
     * Retrieves all batches of messages
     * @return Batch of messages
     */
    public CommandsInfo[] getMessageBatches() {
        return messageBatches;
    }

    public void setMessageBatches(CommandsInfo[] messageBatches) {
    	this.messageBatches = messageBatches;
    }

    /**
     * Retrieves the specified batch of messages
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public CommandsInfo getMessageBatch(int eid) {
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
     * Retrieves the decision round for the last checkpoint
     * @return Decision round for the last checkpoint, or -1 if no checkpoint was yet executed
     */
    public int getLastCheckpointRound() {

        return lastCheckpointRound;
    }

    /**
     * Retrieves the leader for the last checkpoint
     * @return Leader for the last checkpoint, or -1 if no checkpoint was yet executed
     */
    public int getLastCheckpointLeader() {

        return lastCheckpointLeader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DefaultApplicationState) {
            DefaultApplicationState tState = (DefaultApplicationState) obj;

            if ((this.messageBatches != null && tState.messageBatches == null) ||
                    (this.messageBatches == null && tState.messageBatches != null)) {
                //System.out.println("[DefaultApplicationState] returing FALSE1!");
                return false;
            }

            if (this.messageBatches != null && tState.messageBatches != null) {

                if (this.messageBatches.length != tState.messageBatches.length) {
                    //System.out.println("[DefaultApplicationState] returing FALSE2!");
                    return false;
                }
                
                for (int i = 0; i < this.messageBatches.length; i++) {
                    
                    if (this.messageBatches[i] == null && tState.messageBatches[i] != null) {
                        //System.out.println("[DefaultApplicationState] returing FALSE3!");
                        return false;
                    }

                    if (this.messageBatches[i] != null && tState.messageBatches[i] == null) {
                        //System.out.println("[DefaultApplicationState] returing FALSE4!");
                        return false;
                    }
                    
                    if (!(this.messageBatches[i] == null && tState.messageBatches[i] == null) &&
                        (!this.messageBatches[i].equals(tState.messageBatches[i]))) {
                        //System.out.println("[DefaultApplicationState] returing FALSE5!" + (this.messageBatches[i] == null) + " " + (tState.messageBatches[i] == null));
                        return false;
                    }
                }
            }
            return (Arrays.equals(this.stateHash, tState.stateHash) &&
                    tState.lastCheckpointEid == this.lastCheckpointEid &&
                    tState.lastCheckpointRound == this.lastCheckpointRound &&
                    tState.lastCheckpointLeader == this.lastCheckpointLeader &&
                    tState.lastEid == this.lastEid && tState.hasState == this.hasState);
        }
        //System.out.println("[DefaultApplicationState] returing FALSE!");
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + this.lastCheckpointEid;
        hash = hash * 31 + this.lastCheckpointRound;
        hash = hash * 31 + this.lastCheckpointLeader;
        hash = hash * 31 + this.lastEid;
        hash = hash * 31 + (this.hasState ? 1 : 0);
        if (this.stateHash != null) {
            for (int i = 0; i < this.stateHash.length; i++) hash = hash * 31 + (int) this.stateHash[i];
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
