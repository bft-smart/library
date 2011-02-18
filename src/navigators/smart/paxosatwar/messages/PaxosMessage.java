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

package navigators.smart.paxosatwar.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * This class represents a message used in the paxos protocol.
 */
public class PaxosMessage<P> extends SystemMessage {

    private long number; //execution ID for this message TODO: Isto n devia chamar-se 'eid'?
    private int round; // Round number to which this message belongs to
    private int paxosType; // Message type
    private byte[] value = null; // Value used when message type is PROPOSE
    private P proof; // Proof used when message type is COLLECT

    /**
     * Creates a paxos message. Not used. TODO: Q tal meter isto como private?
     * @param in 
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public PaxosMessage(DataInput in) throws IOException{
        super(SystemMessage.Type.PAXOS_MSG,in);

        number = in.readLong();
        round = in.readInt();
        paxosType = in.readInt();

        value = SerialisationHelper.readByteArray(in);

        //WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
        switch(paxosType){
            case MessageFactory.PROPOSE:
                boolean hasProof = in.readBoolean();
                if(hasProof){
                    proof = (P) new Proof(in);
                }
                break;
            case MessageFactory.COLLECT:
                proof = (P) new CollectProof(in);

        }
    }

    /**
     * Creates a paxos message. Used by the message factory to create a COLLECT or PROPOSE message
     * TODO: Q tal meter isto sem quantificador, para ser so visivel no mesmo package?
     * @param paxosType This should be MessageFactory.COLLECT or MessageFactory.PROPOSE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value This should be null if its a COLLECT message, or the proposed value if it is a PROPOSE message
     * @param proof The proof to be sent by the leader for all replicas
     */
    public PaxosMessage(int paxosType, long id,int round,int from, byte[] value, P proof){

        super(SystemMessage.Type.PAXOS_MSG, from);

        this.paxosType = paxosType;
        this.number = id;
        this.round = round;
        this.value = value;
        this.proof = proof;

    }

    /**
     * Creates a paxos message. Used by the message factory to create a WEAK, STRONG, or DECIDE message
     * TODO: Q tal meter isto sem quantificador, para ser so visivel no mesmo package?
     * @param paxosType This should be MessageFactory.WEAK, MessageFactory.STRONG or MessageFactory.DECIDE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value The value decided, or strongly/weakly accepted
     */
    public PaxosMessage(int paxosType, long id,int round,int from, byte[] value) {

        this(paxosType, id, round, from, value, null);

    }

    /**
     * Creates a paxos message. Used by the message factory to create a FREEZE message
     * TODO: Q tal meter isto sem quantificador, para ser so visivel no mesmo package?
     * @param paxosType This should be MessageFactory.FREEZE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     */
    public PaxosMessage(int paxosType, long id,int round, int from) {

        this(paxosType, id, round, from, null, null);

    }

    // Implemented method of the Externalizable interface
    @Override
    public void serialise(DataOutput out) throws IOException {

        super.serialise(out);

        out.writeLong(number);
        out.writeInt(round);
        out.writeInt(paxosType);

        if(value == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(value.length);
            out.write(value);
        }

        //WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
        switch(paxosType){
            case MessageFactory.PROPOSE:
                if(proof != null){
                    out.writeBoolean(true);
                    ((Proof)proof).serialise(out);
                } else {
                    out.writeBoolean(false);
                }
                break;
            case MessageFactory.COLLECT:
                ((CollectProof)proof).serialise(out);

        }
    }
//
//    // Implemented method of the Externalizable interface
//    @Override
//    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//
//        super.readExternal(in);
//
//        number = in.readInt();
//        round = in.readInt();
//        paxosType = in.readInt();
//
//        int toRead = in.readInt();
//
//        if(toRead != -1) {
//
//            value = new byte[toRead];
//
//            do{
//
//                toRead -= in.read(value, value.length-toRead, toRead);
//
//            } while(toRead > 0);
//
//        }
//
//        //WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
//        if(paxosType == MessageFactory.PROPOSE || paxosType == MessageFactory.COLLECT) {
//
//            proof = new CollectProof(in);
//
//        }
//
//    }

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

    /**
     * Returns the proof associated with a PROPOSE or COLLECT message
     * @return The proof
     */
    public P getProof() {

        return proof;

    }

    /**
     * Returns the consensus execution ID of this message
     * @return Consensus execution ID of this message
     */
    public long getNumber() {

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

        switch (paxosType) {
            case MessageFactory.COLLECT:
                return "COLLECT";
            case MessageFactory.DECIDE:
                return "DECIDE";
            case MessageFactory.FREEZE:
                return "FREEZE";
            case MessageFactory.PROPOSE:
                return "PROPOSE";
            case MessageFactory.STRONG:
                return "STRONG";
            case MessageFactory.WEAK:
                return "WEAK";
            default:
                return "";
        }
    }

    // Over-written method
    @Override
    public String toString() {

        return "type="+getPaxosVerboseType()+", number="+getNumber()+", round="+getRound()+", from="+getSender()+", "+

                getProof();

    }

}

