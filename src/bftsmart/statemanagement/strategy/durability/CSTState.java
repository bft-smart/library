package bftsmart.statemanagement.strategy.durability;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.server.defaultservices.CommandsInfo;

/**
 * Stores the data used to transfer the state to a recovering replica.
 * This class serves the three different seeders defined in the CST optimized
 * version for f=1.
 * In that version, the newest replica to take the checkpoint is expected to send
 * the hash of the checkpoint plus the upper portion of the log. The replica which
 * took the checkpoint before that, i.e., the middle replica is expected to send
 * the checkpoint it has plus hashes of the lower and upper portions of the log.
 * The oldest replica to take the checkpoint must send the lower portion of the
 * log.
 * This object must be passed to the state manager class which will combine the
 * replies from the seeders, validating the values and updating the state in the
 * leecher.
 * 
 * @author mhsantos
 *
 */
public class CSTState implements ApplicationState {

	private static final long serialVersionUID = -7624656762922101703L;
	
	private final byte[] hashLogUpper;
	private final byte[] hashLogLower;
	private final byte[] hashCheckpoint;
	
	private final int checkpointEid;
	private final int lastEid;
	
	private final CommandsInfo[] logUpper;
	private final CommandsInfo[] logLower;
	
	private byte[] state;
	
	public CSTState(byte[] state, byte[] hashCheckpoint, CommandsInfo[] logLower, byte[] hashLogLower,
			CommandsInfo[] logUpper, byte[] hashLogUpper, int checkpointEid, int lastEid) {
		setSerializedState(state);
		this.hashLogUpper = hashLogUpper;
		this.hashLogLower = hashLogLower;
		this.hashCheckpoint = hashCheckpoint;
		this.logUpper = logUpper;
		this.logLower = logLower;
		this.checkpointEid = checkpointEid;
		this.lastEid = lastEid;
	}
	
	@Override
	public boolean hasState() {
		return this.getSerializedState() != null;
	}

	@Override
	public byte[] getSerializedState() {
		return state;
	}

	@Override
	public byte[] getStateHash() {
		return hashCheckpoint;
	}
	
	@Override
	public void setSerializedState(byte[] state) {
		this.state = state;
	}

	@Override
	public int getLastEid() {
		return lastEid;
	}

	public int getCheckpointEid() {
		return checkpointEid;
	}

    /**
     * Retrieves the specified batch of messages
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public CommandsInfo getMessageBatch(int eid) {
        if (eid >= checkpointEid && eid <= lastEid) {
        	if(logLower != null) {
        		return logLower[eid - checkpointEid - 1];
        	} else {
        		return logUpper[eid - checkpointEid - 1];
        	}
        } else {
        	return null;
        }
    }
    
	public byte[] getHashLogUpper() {
		return hashLogUpper;
	}

	public byte[] getHashLogLower() {
		return hashLogLower;
	}

	public CommandsInfo[] getLogUpper() {
		return logUpper;
	}

	public CommandsInfo[] getLogLower() {
		return logLower;
	}
    
	public byte[] getHashCheckpoint() {
		return hashCheckpoint;
	}
}
