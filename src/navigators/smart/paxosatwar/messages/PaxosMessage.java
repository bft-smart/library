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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import navigators.smart.tom.core.messages.Serialisable;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * This class represents a message used in the paxos protocol.
 */
public class PaxosMessage<P extends Serialisable> extends SystemMessage {

    private long number; //execution ID for this message TODO: Isto n devia chamar-se 'eid'?
    private int round; // Round number to which this message belongs to
    private int paxosType; // Message type
    private byte[] value; // Value used when message type is PROPOSE
    private P proof; // Proof used when message type is COLLECT

    /**
     * Creates a paxos message. Not used. TODO: Q tal meter isto como private?
     * @param in 
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public PaxosMessage(ByteBuffer in) {
        super(Type.PAXOS_MSG,in);

		number = in.getLong();
		round = in.getInt();
		paxosType = in.getInt();

		value = SerialisationHelper.readByteArray(in);

		// WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
		switch (paxosType) {
		case MessageFactory.PROPOSE:
			boolean hasProof = in.get() == 1 ? true : false;
			if (hasProof) {
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
    public void serialise(ByteBuffer out)  {

        super.serialise(out);

		out.putLong(number);
		out.putInt(round);
		out.putInt(paxosType);

		SerialisationHelper.writeByteArray(value, out);

		// WEAK, STRONG, DECIDE and FREEZE does not have associated proofs
		switch (paxosType) {
		case MessageFactory.PROPOSE:
			if (proof != null) {
				out.put((byte)1);
				proof.serialise(out);
			} else {
				out.put((byte)0);
			}
			break;
		case MessageFactory.COLLECT:
			 proof.serialise(out);

        }
    }
    
    @Override
    public int getMsgSize(){
    	int ret = super.getMsgSize();
    	 switch(paxosType){
         case MessageFactory.PROPOSE:
        	 ret +=1;
             if(proof != null){
                 ret += proof.getMsgSize();
             } 
             break;
         case MessageFactory.COLLECT:
             ret += proof.getMsgSize();

     }
    	
    	return ret += 20 + (value!=null ? value.length : 0 ); //8+4+4+4+value.length
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

                getProof()+" content: "+Arrays.toString(value);

    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (number ^ (number >>> 32));
		result = prime * result + paxosType;
		result = prime * result + ((proof == null) ? 0 : proof.hashCode());
		result = prime * result + round;
		result = prime * result + Arrays.hashCode(value);
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof PaxosMessage<?>))
			return false;
		PaxosMessage<?> other = (PaxosMessage<?>) obj;
		if (number != other.number)
			return false;
		if (paxosType != other.paxosType)
			return false;
		if (proof == null) {
			if (other.proof != null)
				return false;
		} else if (!proof.equals(other.proof))
			return false;
		if (round != other.round)
			return false;
		if (!Arrays.equals(value, other.value))
			return false;
		return true;
	}

}

