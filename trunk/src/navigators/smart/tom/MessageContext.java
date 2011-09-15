/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom;

import navigators.smart.tom.core.messages.TOMMessage;

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
    private int view;
    private int consensusId;
    private int sender;
    private TOMMessage firstInBatch; //to be replaced by a statistics class

    public MessageContext(long timestamp, byte[] nonces, int view, int consensusId, int sender, TOMMessage firstInBatch) {
        this.timestamp = timestamp;
        this.nonces = nonces;
        this.view = view;
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
     * @return the view
     */
    public int getView() {
        return view;
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

}
