package bftsmart.aware.messages;


/**
 * This class work as a factory of messages used in the paxos protocol.
 *
 * @author cb
 */
public class MonitoringMessageFactory {


    public static final int DUMMY_PROPOSE = 44791;
    public static final int PROPOSE_RESPONSE = 44792;
    public static final int WRITE_RESPONSE = 44793;

    private int from; // Replica ID of the process which sent this message

    /**
     * Creates a message factory
     *
     * @param from Replica ID of the process which sent this message
     */
    public MonitoringMessageFactory(int from) {
        this.from = from;
    }

    /**
     * Creates a DUMMY_PROPOSE message to be sent by this process.
     * The DUMMY_PROPOSE is used to estimate Propose latencies of non-leaders
     *
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Proposed value
     * @return A monitoring message of the DUMMY_PROPOSE type, with the specified id, epoch, value, and proof
     */
    public MonitoringMessage createDummyPropose(int id, int epoch, byte[] value) {
        return new MonitoringMessage(DUMMY_PROPOSE, id, epoch, from, value);
    }

    /**
     * Creates a PROPOSE_RESPONSE message to be sent by this process
     *
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Proposed value
     * @return A monitoring message of the PROPOSE-RESPONSE type
     */
    public MonitoringMessage createProposeResponse(int id, int epoch, byte[] value) {
        return new MonitoringMessage(PROPOSE_RESPONSE, id, epoch, from, value);
    }

    /**
     * Creates a PROPOSE_RESPONSE message to be sent by this process
     *
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Proposed value
     * @param challenge random number attached to the message for preventing ahead-of-time responses
     * @return A monitoring message of the PROPOSE-RESPONSE type
     */
    public MonitoringMessage createProposeResponse(int id, int epoch, int challenge, byte[] value) {
        return new MonitoringMessage(PROPOSE_RESPONSE, id, epoch, from, challenge, value);
    }

    /**
     * Creates a WRITE message to be sent by this process
     *
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Write value
     * @return A monitoring message of the WRITE_RESPONSE type
     */
    public MonitoringMessage createWriteResponse(int id, int epoch, byte[] value) {
        return new MonitoringMessage(WRITE_RESPONSE, id, epoch, from, value);
    }

    /**
     * Creates a WRITE message to be sent by this process
     *
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Write value
     * @param challenge random number attached to the message for preventing ahead-of-time responses
     * @return A monitoring message of the WRITE_RESPONSE type
     */
    public MonitoringMessage createWriteResponse(int id, int epoch, int challenge, byte[] value) {
        return new MonitoringMessage(WRITE_RESPONSE, id, epoch, from, challenge, value);
    }

}

