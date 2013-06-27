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
package bftsmart.paxosatwar.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import bftsmart.communication.SystemMessage;



/**
 * This class represents a message used in the paxos protocol.
 */
public class PaxosMessage extends SystemMessage {

    private int number; //execution ID for this message TODO: Shouldn't this be called 'eid'?
    private int round; // Round number to which this message belongs to
    private int paxosType; // Message type
    private byte[] value = null; // Value used when message type is PROPOSE
    private Object macVector; // Proof used when message type is COLLECT

    /**
     * Creates a paxos message. Not used. TODO: How about making it private?
     */
    public PaxosMessage(){}

    /**
     * Creates a paxos message. Used by the message factory to create a COLLECT or PROPOSE message
     * TODO: How about removing the modifier, to make it visible just within the package?
     * @param paxosType This should be MessageFactory.COLLECT or MessageFactory.PROPOSE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value This should be null if its a COLLECT message, or the proposed value if it is a PROPOSE message
     * @param proof The proof to be sent by the leader for all replicas
     */
    public PaxosMessage(int paxosType, int id,int round,int from, byte[] value, Object proof){

        super(from);

        this.paxosType = paxosType;
        this.number = id;
        this.round = round;
        this.value = value;
        this.macVector = proof;

    }

    /**
     * Creates a paxos message. Used by the message factory to create a WEAK, STRONG, or DECIDE message
     * TODO: How about removing the modifier, to make it visible just within the package?
     * @param paxosType This should be MessageFactory.WEAK, MessageFactory.STRONG or MessageFactory.DECIDE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value The value decided, or strongly/weakly accepted
     */
    public PaxosMessage(int paxosType, int id,int round,int from, byte[] value) {

        this(paxosType, id, round, from, value, null);

    }

    /**
     * Creates a paxos message. Used by the message factory to create a FREEZE message
     * TODO: How about removing the modifier, to make it visible just within the package?
     * @param paxosType This should be MessageFactory.FREEZE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     */
    public PaxosMessage(int paxosType, int id,int round, int from) {

        this(paxosType, id, round, from, null, null);

    }

    // Implemented method of the Externalizable interface
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {

        super.writeExternal(out);

        out.writeInt(number);
        out.writeInt(round);
        out.writeInt(paxosType);

        if(value == null) {

            out.writeInt(-1);

        } else {

            out.writeInt(value.length);
            out.write(value);

        }

        if(paxosType == MessageFactory.STRONG || paxosType == MessageFactory.COLLECT) {

            out.writeObject(macVector);

        }

    }

    // Implemented method of the Externalizable interface
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

        super.readExternal(in);

        number = in.readInt();
        round = in.readInt();
        paxosType = in.readInt();

        int toRead = in.readInt();

        if(toRead != -1) {

            value = new byte[toRead];

            do{

                toRead -= in.read(value, value.length-toRead, toRead);

            } while(toRead > 0);

        }

        //WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
        if(paxosType == MessageFactory.STRONG) {

            macVector = in.readObject();

        }

    }

    /**
     * Retrieves the round number to which this message belongs
     * @return Round number to which this message belongs
     */
    public int getRound() {

        return round;

    }
    
    /**
     * Retrieves the weakly accepted, strongly accepted, decided, or proposed value.
     * @return The value
     */
    public byte[] getValue() {

        return value;

    }

    public void setMACVector(Object proof) {
        
        this.macVector = proof;
    }
    
    /**
     * Returns the proof associated with a PROPOSE or COLLECT message
     * @return The proof
     */
    public Object getMACVector() {

        return macVector;

    }

    /**
     * Returns the consensus execution ID of this message
     * @return Consensus execution ID of this message
     */
    public int getNumber() {

        return number;

    }

    /**
     * Returns this message type
     * @return This message type
     */
    public int getPaxosType() {

        return paxosType;

    }

    /**
     * Returns this message type as a verbose string
     * @return Message type
     */
    public String getPaxosVerboseType() {
        if (paxosType==MessageFactory.PROPOSE)
            return "PROPOSE";
        else if (paxosType==MessageFactory.STRONG)
            return "STRONG";
        else if (paxosType==MessageFactory.WEAK)
            return "WEAK";
        else
            return "";
    }

    @Override
    public String toString() {
        return "type="+getPaxosVerboseType()+", number="+getNumber()+", round="+
                getRound()+", from="+getSender();
    }

}

