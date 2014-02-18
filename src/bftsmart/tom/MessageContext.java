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
package bftsmart.tom;

import bftsmart.tom.core.messages.TOMMessage;

/**
 * This class represents the whole context of a request ordered in the system.
 * It stores all informations regarding the message sent and the consensus
 * execution that ordered it.
 * 
 * @author alysson
 */
public class MessageContext {
    private long timestamp;
    private byte[] nonces;
    private int regency;
    private int consensusId;
    private int sender;
    private TOMMessage firstInBatch; //to be replaced by a statistics class
    private boolean lastInBatch; // indicates that the command is the last in the batch. Used for logging

    public boolean readOnly = false;
    
    public MessageContext(long timestamp, byte[] nonces, int regency, int consensusId, int sender, TOMMessage firstInBatch) {
        this.timestamp = timestamp;
        this.nonces = nonces;
        this.regency = regency;
        this.consensusId = consensusId;
        this.sender = sender;
        this.firstInBatch = firstInBatch;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return the nonces
     */
    public byte[] getNonces() {
        return nonces;
    }

    /**
     * @return the consensusId
     */
    public int getConsensusId() {
        return consensusId;
    }

    /**
     * @return the regency
     */
    public int getRegency() {
        return regency;
    }

    /**
     * @return the sender
     */
    public int getSender() {
        return sender;
    }

    /**
     * @param sender the sender to set
     */
    public void setSender(int sender) {
        this.sender = sender;
    }
    
    /**
     * @return the first message in the ordered batch
     */
    public TOMMessage getFirstInBatch() {
        return firstInBatch;
    }

    public void setLastInBatch() {
    	lastInBatch = true;
    }
    
    public boolean isLastInBatch() {
    	return lastInBatch;
    }

}
