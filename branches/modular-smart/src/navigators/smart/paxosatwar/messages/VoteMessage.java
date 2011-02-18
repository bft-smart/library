package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;

import navigators.smart.tom.util.SerialisationHelper;

public class VoteMessage extends PaxosMessage {

	/**
	 * The value of this Propose
	 */
	protected byte[] value;

	/**
     * Creates a VoteMessage message
     * @param paxosType This should be MessageFactory.WEAK, .STRONG or .DECIDE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value The proposed value 
     */
    public VoteMessage( int paxosType, long id,int round,int from, byte[] value){
    	super(paxosType,id,round,from);
        this.value = value;
    }
	
	public VoteMessage(ByteBuffer in) {
		super(in);
		value = SerialisationHelper.readByteArray(in);
	}
	
	@Override
	public void serialise(ByteBuffer out){
		super.serialise(out);
		SerialisationHelper.writeByteArray(value, out);
	}
	
	@Override
	public int getMsgSize(){
		int ret = super.getMsgSize();
		return ret += 4 + (value!=null ? value.length : 0 ); // +4 (length field) + value.length
	}

	public VoteMessage(int paxosType, long id, int round, int from) {
		super(paxosType, id, round, from);
	}

	/**
	 * Retrieves the weakly accepted, strongly accepted, decided, or proposed value.
	 * @return The value
	 */
	public byte[] getValue() {
	    return value;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
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
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof VoteMessage))
			return false;
		VoteMessage other = (VoteMessage) obj;
		if (!Arrays.equals(value, other.value))
			return false;
		return true;
	}

}