package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;

public class Collect extends PaxosMessage {
	
	/**
	 * Proof for this collect.
	 */
	private CollectProof proof; 
	
	/**
     * Creates a COLLECT message
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param proof The proof to be sent by the leader for all replicas
     */
    public Collect (long id,int round,int from, CollectProof proof){
    	super(MessageFactory.COLLECT,id,round,from);
        this.proof = proof;
    }

	
	public Collect(ByteBuffer in) {
		super(in);
		proof = new CollectProof(in);
	}

	
	 /**
     * Returns the proof associated with this COLLECT message
     * @return The proof
     */
    public CollectProof getProof() {
        return proof;
    }
    
    // Implemented method of the Externalizable interface
    @Override
    public void serialise(ByteBuffer out)  {
        super.serialise(out);
        proof.serialise(out);
    }
    
	@Override
	public int getMsgSize() {
		int ret = super.getMsgSize();

		ret += proof.getMsgSize();

		return ret;
	}

}
