package bftsmart.statemanagement.strategy.durability;

/**
 * This class is used to define the roles in the Collaborative State Transfer protocol.
 * The recovering replica uses this class to define which replicas should send the
 * checkpoint, log of opperations lower and higher portions
 * 
 * @author Marcel Santos
 */
public class CSTRequestFGT1 extends CSTRequest {
	
	private static final long serialVersionUID = 7661647491651173164L;
	
	private int hashesReplica;
	/** number of requests to be processed in the recovering replica before validate the ckp hash */
	private int nbrHashesBeforeCkp;
	
	/** number of messages that should be in the batch
	 * if the replica is the one to send the checkpoint it will correspond to the actual log
	 */
	private int logSize;
	
	public CSTRequestFGT1(int eid) {
		super(eid);
	}
	
	public int getHashesReplica() {
		return hashesReplica;
	}
	public int getNbrHashesBeforeCkp() {
		return nbrHashesBeforeCkp;
	}
	
	public int getLogSize() {
		return logSize;
	}
	
	@Override
	public void defineReplicas(int[] processes, int globalCkpPeriod, int replicaId) {
    	int N = processes.length;
    	int ckpPeriod = globalCkpPeriod / N;
    	int logSize = (eid + 1) % ckpPeriod;
    	
    	// Next replica that performed the checkpoint
    	// The last checkpoint replica plus all replicas minus one to get the imediate replica before the
    	// checkpoint
    	int indexCkpReplica = (((getEid() - ckpPeriod) % globalCkpPeriod) / ckpPeriod) % N;
    	this.hashesReplica = processes[indexCkpReplica];
    	this.checkpointReplica = processes[(indexCkpReplica + (N - 1)) % N];
    	this.nbrHashesBeforeCkp = ckpPeriod;
		logSize += ckpPeriod;
    	if(this.checkpointReplica == replicaId) { // me
        	this.checkpointReplica = processes[(indexCkpReplica + (N - 2)) % N];
        	this.nbrHashesBeforeCkp = 2 * ckpPeriod;
    		logSize += ckpPeriod;
    	} else if(this.hashesReplica == replicaId) {
    		this.hashesReplica = processes[(indexCkpReplica + (N - 1)) % N];
    		this.checkpointReplica = processes[(indexCkpReplica + (N - 2)) % N];
    		logSize += ckpPeriod;
    	}
    	this.logSize = logSize;
    }
}
