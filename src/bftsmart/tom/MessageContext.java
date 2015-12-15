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
import bftsmart.tom.leaderchange.CertifiedDecision;

import java.io.Serializable;
import java.util.Random;

/**
 * This class represents the whole context of a request ordered in the system.
 * It stores all informations regarding the message sent and the consensus
 * execution that ordered it.
 * 
 * @author alysson
 */
public class MessageContext implements Serializable {
	
	private static final long serialVersionUID = -3757195646384786213L;
	
    private final long timestamp;
    private final int regency;
    private final int leader;
    private final int consensusId;
    private final int numOfNonces;
    private final long seed;

    private final CertifiedDecision proof;
            
    private final TOMMessage firstInBatch; //to be replaced by a statistics class
    private boolean lastInBatch; // indicates that the command is the last in the batch. Used for logging
    private final boolean noOp;
    
    public boolean readOnly = false;
    
    private byte[] nonces;
    
    public MessageContext(long timestamp, int numOfNonces, long seed, int regency, int leader, int consensusId, CertifiedDecision proof, TOMMessage firstInBatch, boolean noOp) {
        this.nonces = null;
        
        this.timestamp = timestamp;
        this.regency = regency;
        this.leader = leader;
        this.consensusId = consensusId;
        this.numOfNonces = numOfNonces;
        this.seed = seed;
        this.proof = proof;
        this.firstInBatch = firstInBatch;
        this.noOp = noOp;
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
        
        if (nonces == null) { //obtain the nonces to be delivered to the application          
            
            nonces = new byte[numOfNonces];
            if (nonces.length > 0) {
                Random rnd = new Random(seed);
                rnd.nextBytes(nonces);
            }
            
        }
        
        return nonces;
    }

    public int getNumOfNonces() {
        return numOfNonces;
    }

    public long getSeed() {
        return seed;
    }
    
    /**
     * @return the consensusId
     */
    public int getConsensusId() {
        return consensusId;
    }
    
    /**
     * 
     * @return the leader with which the batch was decided
     */
    public int getLeader() {
        return leader;
    }
    /**
     * 
     * @return the proof for the consensus
     */
    public CertifiedDecision getProof() {
        return proof;
    }
    
    /**
     * @return the regency
     */
    public int getRegency() {
        return regency;
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

    public boolean isNoOp() {
        return noOp;
    }

}
