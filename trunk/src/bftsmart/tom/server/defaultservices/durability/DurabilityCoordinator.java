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
package bftsmart.tom.server.defaultservices.durability;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.durability.CSTRequest;
import bftsmart.statemanagement.strategy.durability.CSTState;
import bftsmart.statemanagement.strategy.durability.DurableStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;

/**
 * Implements the Collaborative State Transfer protocol. In this protocol, instead of
 * all replicas send the state and log, or state and hash of the log, the replicas
 * are divided into three groups. One group sends the state, another group sends the
 * lower half of the log and the third group sends the upper portion of the log.
 * The replica that receives the state, logs and hashes validates the state and install
 * it.
 * The details of the protocol are described in the paper "On the Efficiency of Durable
 * State Machine Replication".
 *
 * @author Marcel Santos
 */
public abstract class DurabilityCoordinator implements Recoverable, BatchExecutable {

	private ReentrantLock logLock = new ReentrantLock();
	private ReentrantLock hashLock = new ReentrantLock();
	private ReentrantLock stateLock = new ReentrantLock();

	private TOMConfiguration config;

	private MessageDigest md;

	private DurableStateLog log;

	private StateManager stateManager;

	private int lastCkpEid;
    private int globalCheckpointPeriod;
	private int checkpointPortion;
    private int replicaCkpIndex;
	
	public DurabilityCoordinator() {
		try {
			md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
		} catch (NoSuchAlgorithmException ex) {
			java.util.logging.Logger.getLogger(DurabilityCoordinator.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	@Override
	public byte[][] executeBatch(byte[][] commands, MessageContext[] msgCtx) {

		int eid = msgCtx[msgCtx.length-1].getConsensusId();
		
		int[] eids = consensusIds(msgCtx);
		int checkpointIndex = findCheckpointPosition(eids);
		byte[][] replies = new byte[commands.length][];

		// During the execution ids contained in this batch of commands none of the
		// replicas is supposed to take a checkpoint, so the replica will only execute
		// the command and return the replies
		if(checkpointIndex == -1) {
			stateLock.lock();
			replies = appExecuteBatch(commands, msgCtx);
			stateLock.unlock();
			Logger.println("(DurabilityCoordinator.executeBatch) Storing message batch in the state log for consensus " + eid);
			saveCommands(commands, eids);
		} else {
			// there is a replica supposed to take the checkpoint. In this case, the commands
			// has to be executed in two steps. First the batch of commands containing commands
			// until the checkpoint period is executed and the log saved or checkpoint taken
			// if this replica is the one supposed to take the checkpoint. After the checkpoint
			// or log, the pointer in the log is updated and then the remaining portion of the
			// commands is executed
			byte[][] firstHalf = new byte[checkpointIndex + 1][];
			int[] firstHalfEids = new int[firstHalf.length];
			byte[][] secondHalf = new byte[commands.length - (checkpointIndex + 1)][];
			int[] secondHalfEids = new int[secondHalf.length];
			System.arraycopy(commands, 0, firstHalf, 0, checkpointIndex +1);
			System.arraycopy(eids, 0, firstHalfEids, 0, checkpointIndex+1);
			if(secondHalf.length > 0) {
				System.arraycopy(commands, checkpointIndex + 1, secondHalf, 0, commands.length - (checkpointIndex + 1));
				System.arraycopy(eids, checkpointIndex+1, secondHalfEids, 0,  commands.length - (checkpointIndex + 1));
			} else
				firstHalfEids = eids;
			
			byte[][] firstHalfReplies = new byte[firstHalf.length][];
			byte[][] secondHalfReplies = new byte[secondHalf.length][];
			
			// execute the first half
			eid = msgCtx[checkpointIndex].getConsensusId();
			stateLock.lock();
			firstHalfReplies = appExecuteBatch(firstHalf, msgCtx);
			stateLock.unlock();
			
	        if (eid % globalCheckpointPeriod == replicaCkpIndex && lastCkpEid < eid ) {
				Logger.println("(DurabilityCoordinator.executeBatch) Performing checkpoint for consensus " + eid);
				stateLock.lock();
				byte[] snapshot = getSnapshot();
				stateLock.unlock();
				saveState(snapshot, eid, 0, 0);
				lastCkpEid = eid;
			} else {
				Logger.println("(DurabilityCoordinator.executeBatch) Storing message batch in the state log for consensus " + eid);
				saveCommands(firstHalf, firstHalfEids);
			}
	        
			System.arraycopy(firstHalfReplies, 0, replies, 0, firstHalfReplies.length);
			
			// execute the second half if it exists
	        if(secondHalf.length > 0) {
//	        	System.out.println("----THERE IS A SECOND HALF----");
				eid = msgCtx[msgCtx.length - 1].getConsensusId();
				stateLock.lock();
				secondHalfReplies = appExecuteBatch(secondHalf, msgCtx);
				stateLock.unlock();
	        	
				Logger.println("(DurabilityCoordinator.executeBatch) Storing message batch in the state log for consensus " + eid);
				saveCommands(secondHalf, secondHalfEids);
				
				System.arraycopy(secondHalfReplies, 0, replies, firstHalfReplies.length, secondHalfReplies.length);
	        }
	        
		}

		return replies;
	}
	
	/**
	 * Iterates over the commands to find if any replica took a checkpoint.
	 * When a replica take a checkpoint, it is necessary to save in an auxiliary table
	 * the position in the log in which that replica took the checkpoint.
	 * It is used during state transfer to find lower or upper log portions to be
	 * restored in the recovering replica.
	 * This iteration over commands is needed due to the batch execution strategy
	 * introduced with the durable techniques to improve state management. As several
	 * consensus instances can be executed in the same batch of commands, it is necessary
	 * to identify if the batch contains checkpoint indexes.

	 * @param msgCtxs the contexts of the consensus where the messages where executed.
	 * There is one msgCtx message for each command to be executed

	 * @return the index in which a replica is supposed to take a checkpoint. If there is
	 * no replica taking a checkpoint during the period comprised by this command batch, it
	 * is returned -1
	 */
	private int findCheckpointPosition(int[] eids) {
		if(config.getGlobalCheckpointPeriod() < 1)
			return -1;
		if(eids.length == 0)
			throw new IllegalArgumentException();
		int firstEid = eids[0];
		if((firstEid + 1) % checkpointPortion == 0) {
			return eidPosition(eids, firstEid);
		} else {
			int nextCkpIndex = (((firstEid / checkpointPortion) + 1) * checkpointPortion) - 1;
			if(nextCkpIndex <= eids[eids.length -1]) {
				return eidPosition(eids, nextCkpIndex);
			}
		}
		return -1;
	}
	
	/**
	 * Iterates over the message contexts to retrieve the index of the last
	 * command executed prior to the checkpoint. That index is used by the
	 * state transfer protocol to find the position of the log commands in
	 * the log file. 
	 * 
	 * @param msgCtx the message context of the commands executed by the replica.
	 * There is one message context for each command
	 * @param eid the eid of the consensus where a replica took a checkpoint
	 * @return the higher position where the eid appears
	 */
	private int eidPosition(int[] eids, int eid) {
		int index = -1;
		if(eids[eids.length-1] == eid)
			return eids.length-1;
		for(int i = 0; i < eids.length; i++) {
			if(eids[i] > eid)
				break;
			index++;
		}
		System.out.println("--- Checkpoint is in position " + index);
		return index;
	}


	@Override
	public ApplicationState getState(int eid, boolean sendState) {
		logLock.lock();
		ApplicationState ret = null;
		logLock.unlock();
		return ret;
	}

	@Override
	public int setState(ApplicationState recvState) {
		int lastEid = -1;
		if (recvState instanceof CSTState) {
			CSTState state = (CSTState) recvState;

			int lastCheckpointEid = state.getCheckpointEid();
			lastEid = state.getLastEid();

			bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) I'm going to update myself from EID "
					+ lastCheckpointEid + " to EID " + lastEid);

			stateLock.lock();
			if(state.getSerializedState() != null) {
				System.out.println("The state is not null. Will install it");
				log.update(state);
				installSnapshot(state.getSerializedState());
			}
			
			System.out.print("--- Installing log from " + (lastCheckpointEid+1) + " to " + lastEid);

			for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
				try {
					bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) interpreting and verifying batched requests for eid " + eid);
					byte[][] commands = state.getMessageBatch(eid).commands;
					if (commands == null || commands.length <= 0) continue;
					appExecuteBatch(commands, null);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}

			}
			System.out.println("--- Installed");
			stateLock.unlock();

		}

		return lastEid;
	}

	private final byte[] computeHash(byte[] data) {
		byte[] ret = null;
		hashLock.lock();
		ret = md.digest(data);
		hashLock.unlock();
		return ret;
	}

	private void saveState(byte[] snapshot, int lastEid, int decisionRound, int leader) {
		logLock.lock();

		Logger.println("(TOMLayer.saveState) Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

		log.newCheckpoint(snapshot, computeHash(snapshot));
		log.setLastEid(-1);
		log.setLastCheckpointEid(lastEid);
		log.setLastCheckpointRound(decisionRound);
		log.setLastCheckpointLeader(leader);

		logLock.unlock();
		Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
	}

	/**
	 * Write commands to log file
	 * @param commands array of commands. Each command is an array of bytes
	 * @param msgCtx
	 */
	private void saveCommands(byte[][] commands, int[] eids) {
		if(!config.isToLog())
			return;
		if(commands.length != eids.length)
			System.out.println("----SIZE OF COMMANDS AND EIDS IS DIFFERENT----");
		logLock.lock();
		int decisionRound = 0;
		int leader = 0;
		
		int eid = eids[0];
		int batchStart = 0;
		for(int i = 0; i <= eids.length; i++) {
			if(i == eids.length) { // the batch command contains only one command or it is the last position of the array
				byte[][] batch = Arrays.copyOfRange(commands, batchStart, i);
				log.addMessageBatch(batch, decisionRound, leader);
				log.setLastEid(eid, globalCheckpointPeriod, checkpointPortion);
//				if(batchStart > 0)
//					System.out.println("Last batch: " + commands.length + "," + batchStart + "-" + i + "," + batch.length);
			} else {
				if(eids[i] > eid) { // saves commands when the eid changes or when it is the last batch
					byte[][] batch = Arrays.copyOfRange(commands, batchStart, i);
//					System.out.println("THERE IS MORE THAN ONE EID in this batch." + commands.length + "," + batchStart + "-" + i + "," + batch.length);
					log.addMessageBatch(batch, decisionRound, leader);
					log.setLastEid(eid, globalCheckpointPeriod, checkpointPortion);
					eid = eids[i];
					batchStart = i;
				}
			}
		}
		logLock.unlock();
	}


	public CSTState getState(CSTRequest cstRequest) {
		CSTState ret = log.getState(cstRequest);
		return ret;
	}

	@Override
	public void setReplicaContext(ReplicaContext replicaContext) {
		this.config = replicaContext.getStaticConfiguration();
		if(log == null) {
	        globalCheckpointPeriod = config.getGlobalCheckpointPeriod();
	        replicaCkpIndex = getCheckpointPortionIndex();
	        checkpointPortion = globalCheckpointPeriod / config.getN();
	        
//			byte[] state = getSnapshot();
			if(config.isToLog()) {
				int replicaId = config.getProcessId();
				boolean isToLog = config.isToLog();
				boolean syncLog = config.isToWriteSyncLog();
				boolean syncCkp = config.isToWriteSyncCkp();
//				log = new DurableStateLog(replicaId, state, computeHash(state), isToLog, syncLog, syncCkp);
				log = new DurableStateLog(replicaId, null, null, isToLog, syncLog, syncCkp);
			}
		}
	}

    private int getCheckpointPortionIndex() {
    	int numberOfReplicas = config.getN();
    	int ckpIndex = ((globalCheckpointPeriod / numberOfReplicas) * (config.getProcessId() + 1)) -1;
    	return ckpIndex;
    }
    

    /**
     * Iterates over the message context array and get the consensus id of each command
     * being executed. As several times during the execution of commands and logging the
     * only infomation important in MessageContext is the consensus id, it saves time to
     * have it already in an array of ids
     * @param ctxs the message context, one for each command to be executed
     * @return the id of the consensus decision for each command
     */
    private int[] consensusIds(MessageContext[] ctxs) {
    	int[] eids = new int[ctxs.length];
    	for(int i = 0; i < ctxs.length; i++)
    		eids[i] = ctxs[i].getConsensusId();
    	return eids;
    }

    @Override
	public StateManager getStateManager() {
		if(stateManager == null)
			stateManager = new DurableStateManager();
		return stateManager;
	}

	public byte[] getCurrentStateHash() {
		byte[] currentState = getSnapshot();
		byte[] currentStateHash = TOMUtil.computeHash(currentState);
		System.out.println("--- State size: " + currentState.length + " Current state Hash: " + Arrays.toString(currentStateHash));
		return currentStateHash;
	}

	public abstract void installSnapshot(byte[] state);
	public abstract byte[] getSnapshot();
	public abstract byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs);
}
