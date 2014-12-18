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

import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultApplicationState;

/**
 * This classes serves as a log for the state associated with the last checkpoint, and the message
 * batches received since the same checkpoint until the present. The state associated with the last
 * checkpoint together with all the batches of messages received so far, comprises this replica
 * current state
 * 
 * @author Jo�o Sousa
 */
public class StateLog {

    private CommandsInfo[] messageBatches; // batches received since the last checkpoint.
    private int lastCheckpointEid; // Execution ID for the last checkpoint
    private int lastCheckpointRound; // Decision round for the last checkpoint
    private int lastCheckpointLeader; // Leader for the last checkpoint
    private byte[] state; // State associated with the last checkpoint
    private byte[] stateHash; // Hash of the state associated with the last checkpoint
    private int position; // next position in the array of batches to be written
    private int lastEid; // Execution ID for the last messages batch delivered to the application

    /**
     * Constructs a State log
     * @param k The chekpoint period
     */
    public StateLog(int k, byte[] initialState, byte[] initialHash) {

        this.messageBatches = new CommandsInfo[k - 1];
        this.lastCheckpointEid = -1;
        this.lastCheckpointRound = -1;
        this.lastCheckpointLeader = -1;
        this.state = initialState;
        this.stateHash = initialHash;
        this.position = 0;
        this.lastEid = -1;
    }
    
    /**
     * Constructs a State log
     * @param k The chekpoint period
     */
    public StateLog(int k) {

        this.messageBatches = new CommandsInfo[k - 1];
        this.lastCheckpointEid = -1;
        this.lastCheckpointRound = -1;
        this.lastCheckpointLeader = -1;
        this.state = null;
        this.stateHash = null;
        this.position = 0;
        this.lastEid = -1;
    }

    public StateLog(byte[] initialState, byte[] initialHash) {
        this.lastCheckpointEid = -1;
        this.lastCheckpointRound = -1;
        this.lastCheckpointLeader = -1;
        this.state = initialState;
        this.stateHash = initialHash;
        this.lastEid = -1;
	}
    
    /**
     * Sets the state associated with the last checkpoint, and updates the execution ID associated with it
     * @param state State associated with the last checkpoint
     */
    public void newCheckpoint(byte[] state, byte[] stateHash, int lastConsensusId) {

    	if(messageBatches != null) {
    		for (int i = 0; i < this.messageBatches.length; i++)
                messageBatches[i] = null;
    	}

        position = 0;
        this.state = state;
        this.stateHash = stateHash;
                       
    }

    /**
     * Sets the execution ID for the last checkpoint
     * @param lastCheckpointEid Execution ID for the last checkpoint
     */
    public void setLastCheckpointEid(int lastCheckpointEid) {
        this.lastCheckpointEid = lastCheckpointEid;
    }

    /**
     * Retrieves the execution ID for the last checkpoint
     * @return Execution ID for the last checkpoint, or -1 if none was obtained
     */
    public int getLastCheckpointEid() {
        
        return lastCheckpointEid ;
    }

    /**
     * Sets the decision round for the last checkpoint
     * @param lastCheckpointRound Decision round for the last checkpoint
     */
    public void setLastCheckpointRound(int lastCheckpointRound) {

        this.lastCheckpointRound = lastCheckpointRound;
    }

    /**
     * Retrieves the decision round for the last checkpoint
     * @return Decision round for the last checkpoint, or -1 if none was obtained
     */
    public int getLastCheckpointRound() {

        return lastCheckpointRound ;
    }

    /**
     * Sets the leader for the last checkpoint
     * @param lastCheckpointLeader Leader for the last checkpoint
     */
    public void setLastCheckpointLeader(int lastCheckpointLeader) {

        this.lastCheckpointLeader = lastCheckpointLeader;
    }

    /**
     * Retrieves the leader for the last checkpoint
     * @return Leader for the last checkpoint, or -1 if none was obtained
     */
    public int getLastCheckpointLeader() {

        return lastCheckpointLeader;
    }

    /**
     * Sets the execution ID for the last messages batch delivered to the application
     * @param lastEid the execution ID for the last messages batch delivered to the application
     */
    public void setLastEid(int lastEid) {

       this.lastEid = lastEid;
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
     * Adds a message batch to the log. This batches should be added to the log
     * in the same order in which they are delivered to the application. Only
     * the 'k' batches received after the last checkpoint are supposed to be kept
     * @param commands The batch of messages to be kept.
     * @param round the round in which the messages were ordered
     * @param leader the leader by the moment the messages were ordered
     */
    public void addMessageBatch(byte[][] commands, int round, int leader, int lastConsensusId) {
        if (position < messageBatches.length) {
            messageBatches[position] = new CommandsInfo(commands, round, leader);
            position++;
        }
        setLastEid(lastConsensusId);
    }

    /**
     * Returns a batch of messages, given its correspondent execution ID
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public CommandsInfo getMessageBatch(int eid) {
        if (eid > lastCheckpointEid && eid <= lastEid) {
            return messageBatches[eid - lastCheckpointEid - 1];
        }
        else return null;
    }

    /**
     * Retrieves all the stored batches kept since the last checkpoint
     * @return All the stored batches kept since the last checkpoint
     */
    public CommandsInfo[] getMessageBatches() {
        return messageBatches;
    }

    /**
     * Retrieves the total number of stored batches kept since the last checkpoint
     * @return The total number of stored batches kept since the last checkpoint
     */
    public int getNumBatches() {
        return position;
    }
    /**
     * Constructs a TransferableState using this log information
     * @param eid Execution ID correspondent to desired state
     * @return TransferableState Object containing this log information
     */
    public DefaultApplicationState getApplicationState(int eid, boolean setState) {

    	System.out.println("--- Eid requested: " + eid + ". Last checkpoint: " + lastCheckpointEid + ". Last EID: " + this.lastEid);
        CommandsInfo[] batches = null;

        int lastEid = -1;
       
        if (eid >= lastCheckpointEid && eid <= this.lastEid) {
            
    	System.out.println("--- Constructing ApplicationState up until EID " + eid);

            int size = eid - lastCheckpointEid ;

            if (size > 0) {
                batches = new CommandsInfo[size];

                for (int i = 0; i < size; i++)
                    batches[i] = messageBatches[i];
            }
            lastEid = eid;
            return new DefaultApplicationState(batches, lastCheckpointEid, lastCheckpointRound, lastCheckpointLeader, lastEid, (setState ? state : null), stateHash);

        }
        else return null;
    }

    /**
     * Updates this log, according to the information contained in the TransferableState object
     * @param transState TransferableState object containing the information which is used to updated this log
     */
    public void update(DefaultApplicationState transState) {

        position = 0;
        if (transState.getMessageBatches() != null) {
            for (int i = 0; i < transState.getMessageBatches().length; i++, position = i) {
                this.messageBatches[i] = transState.getMessageBatches()[i];
            }
        }

        this.lastCheckpointEid = transState.getLastCheckpointEid();

        this.state = transState.getState();

        this.stateHash = transState.getStateHash();

        this.lastEid = transState.getLastEid();
    }

}
