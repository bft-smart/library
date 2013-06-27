package bftsmart.statemanagement.strategy.durability;

import java.io.Serializable;

/**
 * This class is used to define the roles in the Collaborative State Transfer protocol.
 * The recovering replica uses this class to define which replicas should send the
 * checkpoint, log of opperations lower and higher portions
 * 
 * @author Marcel Santos
 */
public abstract class CSTRequest implements Serializable {
	
	private static final long serialVersionUID = 7463498141366035002L;
	
	protected int eid;
	/** id of the replica responsible for sending the checkpoint;*/
	protected int checkpointReplica;
	
	public CSTRequest(int eid) {
		this.eid = eid;
	}
	
	public int getEid() {
		return eid;
	}
	
	public int getCheckpointReplica() {
		return checkpointReplica;
	}

	public abstract void defineReplicas(int[] processes, int globalCkpPeriod, int replicaId);

}
