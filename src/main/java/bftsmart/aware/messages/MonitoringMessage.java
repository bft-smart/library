package bftsmart.aware.messages;

import bftsmart.consensus.messages.ConsensusMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


/**
 * This class represents a message used for monitoring the consensus protocol.
 *
 * @author cb
 */
public class MonitoringMessage extends ConsensusMessage {

    public MonitoringMessage() {
    }

    /**
     * Creates a monitoring message. Used by the message factory to create a COLLECT or PROPOSE message
     *
     * @param paxosType DUMMY_PROPOSE, PROPOSE_RESPONSE, or WRITE_RESPONSE
     * @param id        Consensus's ID
     * @param epoch     Epoch timestamp
     * @param from      This should be this process ID
     * @param value     This should be null if its a COLLECT message, or the proposed value if it is a PROPOSE message
     */
    public MonitoringMessage(int paxosType, int id, int epoch, int from, byte[] value) {
        super(paxosType, id, epoch, from, value);
    }

    /**
     * Creates a monitoring message. Used by the message factory to create a COLLECT or PROPOSE message
     *
     * @param paxosType DUMMY_PROPOSE, PROPOSE_RESPONSE, or WRITE_RESPONSE
     * @param id        Consensus's ID
     * @param epoch     Epoch timestamp
     * @param from      This should be this process ID
     * @param value     This should be null if its a COLLECT message, or the proposed value if it is a PROPOSE message
     * @param challenge random number attached to the message for preventing ahead-of-time responses
     */
    public MonitoringMessage(int paxosType, int id, int epoch, int from, int challenge, byte[] value) {
        super(paxosType, id, epoch, from, value);
        this.challenge = challenge;
    }


    /**
     * Creates a monitoring message. Used by the message factory to create a FREEZE message
     *
     * @param type  This should be MessageFactory.FREEZE
     * @param id    Consensus's consensus ID
     * @param epoch Epoch timestamp
     * @param from  This should be this process ID
     */
    public MonitoringMessage(int type, int id, int epoch, int from) {

        super(type, id, epoch, from, null);

    }

    @Override
    public String getPaxosVerboseType() {
        if (paxosType == MonitoringMessageFactory.DUMMY_PROPOSE)
            return "DUMMY_PROPOSE";
        else if (paxosType == MonitoringMessageFactory.PROPOSE_RESPONSE)
            return "PROPOSE_RESPONSE";
        else if (paxosType == MonitoringMessageFactory.WRITE_RESPONSE)
            return "WRITE_RESPONSE";
        else
            return "";
    }

    @Override
    public String toString() {
        return "type=" + getPaxosVerboseType() + ", consensusID=" + this.getNumber() + ", epoch=" +
                getEpoch() + ", from=" + getSender();
    }


    // Implemented method of the Externalizable interface
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeInt(challenge);
        super.writeExternal(out);


    }

    // Implemented method of the Externalizable interface
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

        challenge = in.readInt();
        super.readExternal(in);

    }

}

