/**
 * 
 */
package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;

/**
 * @author spann
 *
 */
public class Propose extends VoteMessage {
	
	/**
	 * Proof for a already started round...l
	 */
	private Proof proof; 
	
	public Propose( ByteBuffer in) {
		super(in);
		
		boolean hasProof = in.get() == 1 ? true : false;
		if (hasProof) {
			proof = new Proof(in);
		} else { 
			proof = null;
		}
	}

	/**
     * Creates a PROPOSE message
     * @param paxosType This should be MessageFactory.COLLECT or MessageFactory.PROPOSE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value The proposed value 
     * @param proof The proof to be sent by the leader for all replicas
     */
    public Propose(long id,int round,int from, byte[] value, Proof proof){
    	super(MessageFactory.PROPOSE,id,round,from, value);
        this.proof = proof;
    }

	/**
     * Returns the proof associated with a PROPOSE or COLLECT message
     * @return The proof
     */
    public Proof getProof() {
        return proof;
    }
    
    // Implemented method of the Externalizable interface
    @Override
	public void serialise(ByteBuffer out) {
		super.serialise(out);

		if (proof != null) {
			out.put((byte) 1);
			proof.serialise(out);
		} else {
			out.put((byte) 0);
		}

	}
    
    @Override
	public int getMsgSize() {
		int ret = super.getMsgSize();

		ret += 1;
		if (proof != null) {
			ret += proof.getMsgSize();
		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((proof == null) ? 0 : proof.hashCode());
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
		if (!(obj instanceof Propose))
			return false;
		Propose other = (Propose) obj;
		if (proof == null) {
			if (other.proof != null)
				return false;
		} else if (!proof.equals(other.proof))
			return false;
		return true;
	}

}
