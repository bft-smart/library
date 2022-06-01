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
package bftsmart.consensus.messages;

/**
 * This class work as a factory of messages used in the paxos protocol.
 */
public class MessageFactory {

    // constants for messages types
    public static final int PROPOSE = 44781;
    public static final int WRITE = 44782;
    public static final int ACCEPT = 44783;
    public static final int AUDIT = 44784;
    public static final int STORAGE = 44785;

    private int from; // Replica ID of the process which sent this message

    /**
     * Creates a message factory
     * 
     * @param from Replica ID of the process which sent this message
     */
    public MessageFactory(int from) {

        this.from = from;

    }

    /**
     * Creates a PROPOSE message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Proposed value
     * @param proof Proofs from other replicas
     * @return A paxos message of the PROPOSE type, with the specified id, epoch,
     *         value, and proof
     */
    public ConsensusMessage createPropose(int id, int epoch, byte[] value) {

        return new ConsensusMessage(PROPOSE, id, epoch, from, value);

    }

    /**
     * Creates a WRITE message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Write value
     * @return A consensus message of the WRITE type, with the specified id, epoch,
     *         and value
     */
    public ConsensusMessage createWrite(int id, int epoch, byte[] value) {

        return new ConsensusMessage(WRITE, id, epoch, from, value);

    }

    /**
     * Creates a WRITE message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Accepted value
     * @return A consensus message of the ACCEPT type, with the specified id, epoch,
     *         and value
     */
    public ConsensusMessage createAccept(int id, int epoch, byte[] value) {

        return new ConsensusMessage(ACCEPT, id, epoch, from, value);

    }

    /*************************** FORENSICS METHODS *******************************/

    /**
     * Creates a AUDIT message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Audit value, should contain cid intervals
     * @return A consensus message of the AUDIT type, with the specified id, epoch,
     *         and value
     */
    public ConsensusMessage createAudit(int id, int epoch) {
        return new ConsensusMessage(AUDIT, id, epoch, from, null); // id and epoch should not be important
    }

    /**
     * Creates a AUDIT message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param value Audit value, should contain cid intervals
     * @return A consensus message of the AUDIT type, with the specified id, epoch,
     *         and value
     */
    public ConsensusMessage createAudit(int id) {
        return new ConsensusMessage(AUDIT, id, 0, from, null); // id and epoch should not be important
    }

    /**
     * Creates a AUDIT message sended by the client
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value Audit value, should contain cid intervals
     * @return A consensus message of the AUDIT type, with the specified id, epoch,
     *         and value
     */
    public ConsensusMessage createAudit(int client, byte[] value) {
        return new ConsensusMessage(AUDIT, -1, -1, client, value); // id and epoch should not be important
    }

    /**
     * Creates a STORAGE message to be sent by this process
     * 
     * @param id    Consensus's execution ID
     * @param epoch Epoch number
     * @param value STORAGE value, should be the storage
     * @return A consensus message of the STORAGE type, with the specified id,
     *         epoch,
     *         and value
     */
    public ConsensusMessage createStorage(int id, int epoch, byte[] value) {
        return new ConsensusMessage(STORAGE, id, epoch, from, value); // id and epoch should not be important
    }

    public ConsensusMessage createStorage(byte[] value) {
        return new ConsensusMessage(STORAGE, -1, -1, from, value); // id and epoch should not be important
    }


}
