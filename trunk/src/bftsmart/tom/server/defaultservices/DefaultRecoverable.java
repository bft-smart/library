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
package bftsmart.tom.server.defaultservices;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.Logger;

/**
 *
 * @author Joao Sousa
 */
public abstract class DefaultRecoverable implements Recoverable, BatchExecutable {
    
	private int checkpointPeriod;

    private ReentrantLock logLock = new ReentrantLock();
    private ReentrantLock hashLock = new ReentrantLock();
    private ReentrantLock stateLock = new ReentrantLock();
    
    private TOMConfiguration config;

    private MessageDigest md;
        
    private StateLog log;
    
    private StateManager stateManager;
    
    public DefaultRecoverable() {

        try {
            md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (NoSuchAlgorithmException ex) {
            java.util.logging.Logger.getLogger(DefaultRecoverable.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public byte[][] executeBatch(byte[][] commands, MessageContext[] msgCtxs) {
        
        int eid = msgCtxs[0].getConsensusId();
            
        stateLock.lock();
        byte[][] replies = appExecuteBatch(commands, msgCtxs);
        stateLock.unlock();
        
        if ((eid > 0) && ((eid % checkpointPeriod) == 0)) {
            Logger.println("(DurabilityCoordinator.executeBatch) Performing checkpoint for consensus " + eid);
            stateLock.lock();
            byte[] snapshot = getSnapshot();
            stateLock.unlock();
            saveState(snapshot, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
        } else {
            Logger.println("(DurabilityCoordinator.executeBatch) Storing message batch in the state log for consensus " + eid);
            saveCommands(commands, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
        }

        return replies;
    }

    
    public final byte[] computeHash(byte[] data) {
        byte[] ret = null;
        hashLock.lock();
        ret = md.digest(data);
        hashLock.unlock();

        return ret;
    }
    
    private StateLog getLog() {
        return log;
    }
    
    private void saveState(byte[] snapshot, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        logLock.lock();

        Logger.println("(TOMLayer.saveState) Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.newCheckpoint(snapshot, computeHash(snapshot));
        thisLog.setLastEid(-1);
        thisLog.setLastCheckpointEid(lastEid);
        thisLog.setLastCheckpointRound(decisionRound);
        thisLog.setLastCheckpointLeader(leader);

        logLock.unlock();
        Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void saveCommands(byte[][] commands, int lastEid, int decisionRound, int leader) {
        StateLog thisLog = getLog();

        logLock.lock();

        Logger.println("(TOMLayer.saveBatch) Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.addMessageBatch(commands, decisionRound, leader);
        thisLog.setLastEid(lastEid);

        logLock.unlock();
        
        Logger.println("(TOMLayer.saveBatch) Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    @Override
    public ApplicationState getState(int eid, boolean sendState) {
        logLock.lock();
        ApplicationState ret = (eid > -1 ? getLog().getApplicationState(eid, sendState) : new DefaultApplicationState());
        logLock.unlock();
        return ret;
    }
    
    @Override
    public int setState(ApplicationState recvState) {
        
        int lastEid = -1;
        if (recvState instanceof DefaultApplicationState) {
            
            DefaultApplicationState state = (DefaultApplicationState) recvState;
            
            getLog().update(state);
            
            int lastCheckpointEid = state.getLastCheckpointEid();
            
            lastEid = state.getLastEid();
            //lastEid = lastCheckpointEid + (state.getMessageBatches() != null ? state.getMessageBatches().length : 0);

            bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) I'm going to update myself from EID "
                    + lastCheckpointEid + " to EID " + lastEid);

            stateLock.lock();
            installSnapshot(state.getState());

            // INUTIL??????
            //tomLayer.lm.addLeaderInfo(lastCheckpointEid, state.getLastCheckpointRound(),
            //        state.getLastCheckpointLeader());
            
            for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
                try {
                    
                    bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) interpreting and verifying batched requests for eid " + eid);
                    if (state.getMessageBatch(eid) == null) System.out.println("(DurabilityCoordinator.setState) " + eid + " NULO!!!");
                    
                    byte[][] commands = state.getMessageBatch(eid).commands; // take a batch

                    if (commands == null || commands.length <= 0) continue;
                    appExecuteBatch(commands, null);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    if (e instanceof ArrayIndexOutOfBoundsException) {
                        System.out.println("Eid do ultimo checkpoint: " + state.getLastCheckpointEid());
                        System.out.println("Eid do ultimo consenso: " + state.getLastEid());
                        System.out.println("numero de mensagens supostamente no batch: " + (state.getLastEid() - state.getLastCheckpointEid() + 1));
                        System.out.println("numero de mensagens realmente no batch: " + state.getMessageBatches().length);
                    }
                }

            }
            stateLock.unlock();

        }

        return lastEid;
    }
        
    @Override
    public void setReplicaContext(ReplicaContext replicaContext) {
    	this.config = replicaContext.getStaticConfiguration();
    	if(log == null) {
    		checkpointPeriod = config.getCheckpointPeriod();
            byte[] state = getSnapshot();
            if(config.isToLog() && config.logToDisk()) {
            	int replicaId = config.getProcessId();
            	boolean isToLog = config.isToLog();
            	boolean syncLog = config.isToWriteSyncLog();
            	boolean syncCkp = config.isToWriteSyncCkp();
            	log = new DiskStateLog(replicaId, state, computeHash(state), isToLog, syncLog, syncCkp);
            } else
            	log = new StateLog(checkpointPeriod, state, computeHash(state));
    	}
    }
    
    @Override
    public StateManager getStateManager() {
    	if(stateManager == null)
    		stateManager = new StandardStateManager();
    	return stateManager;
    }
    
    public abstract void installSnapshot(byte[] state);
    public abstract byte[] getSnapshot();
    public abstract byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs);
}
