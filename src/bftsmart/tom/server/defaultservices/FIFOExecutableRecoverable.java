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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.server.FIFOExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.Logger;

/**
 *
 * @author Marcel Santos
 */
public abstract class FIFOExecutableRecoverable implements Recoverable, FIFOExecutable {
    
    public static final int CHECKPOINT_PERIOD = 0;

    private ReentrantLock logLock = new ReentrantLock();
    private ReentrantLock hashLock = new ReentrantLock();
    private ReentrantLock stateLock = new ReentrantLock();
    
    private MessageDigest md;
        
    private StateLog log;
    private List<byte[]> commands = new ArrayList<byte[]>();
    
    private StateManager stateManager;
    
    protected ConcurrentMap<Integer, Integer> clientOperations;
    private Lock fifoLock = new ReentrantLock();
    private Condition updatedState = fifoLock.newCondition();
    
    public FIFOExecutableRecoverable() {
    	clientOperations = new ConcurrentHashMap<Integer, Integer>();
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            java.util.logging.Logger.getLogger(FIFOExecutableRecoverable.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(CHECKPOINT_PERIOD > 0)
        	log = new StateLog(CHECKPOINT_PERIOD);
    }
    
    
    @Override
    public byte[] executeOrderedFIFO(byte[] command, MessageContext msgCtx, int clientId, int operationId) {
        int eid = msgCtx.getConsensusId();
        byte[] reply = null;
        fifoLock.lock();
        if(operationId == 0) {
            stateLock.lock();
            reply = executeOrdered(command, msgCtx);
            stateLock.unlock();
            clientOperations.put(clientId, operationId + 1);
        } else {
        	while(clientOperations.get(clientId) == null || clientOperations.get(clientId) < operationId) {
        		try {
					updatedState.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        	}
            stateLock.lock();
            reply = executeOrdered(command, msgCtx);
            stateLock.unlock();
            clientOperations.put(clientId, operationId + 1);
        }
        updatedState.signalAll();
        fifoLock.unlock();
            
        commands.add(command);
        
        if(CHECKPOINT_PERIOD > 0 && msgCtx.isLastInBatch()) {
	        if ((eid > 0) && ((eid % CHECKPOINT_PERIOD) == 0)) {
	            Logger.println("(DurabilityCoordinator.executeBatch) Performing checkpoint for consensus " + eid);
	            stateLock.lock();
	            byte[] snapshot = getSnapshot();
	            stateLock.unlock();
	            saveState(snapshot, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
	        } else {
	            Logger.println("(DurabilityCoordinator.executeBatch) Storing message batch in the state log for consensus " + eid);
	            saveCommands(commands.toArray(new byte[0][]), eid, 0, 0);
	        }
	        commands = new ArrayList<byte[]>();
        }
        return reply;
    }
    
    @Override
    public byte[] executeUnorderedFIFO(byte[] command, MessageContext msgCtx, int clientId, int operationId) {
        byte[] reply = null;
        fifoLock.lock();
        if(operationId == 0) {
            reply = executeUnordered(command, msgCtx);
            clientOperations.put(clientId, operationId + 1);
        } else {
        	while(clientOperations.get(clientId) == null || clientOperations.get(clientId) < operationId) {
        		try {
					updatedState.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        	}
            reply = executeUnordered(command, msgCtx);
            clientOperations.put(clientId, operationId + 1);
        }
        updatedState.signalAll();
        fifoLock.unlock();
        return reply;
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
        /*System.out.println("fiz checkpoint");
        System.out.println("tamanho do snapshot: " + snapshot.length);
        System.out.println("tamanho do log: " + thisLog.getMessageBatches().length);*/
        Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    private void saveCommands(byte[][] commands, int lastEid, int decisionRound, int leader) {
        StateLog thisLog = getLog();

        logLock.lock();

        Logger.println("(TOMLayer.saveBatch) Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.addMessageBatch(commands, decisionRound, leader);
        thisLog.setLastEid(lastEid);

        logLock.unlock();
        
        /*System.out.println("guardei comandos");
        System.out.println("tamanho do log: " + thisLog.getNumBatches());*/
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
            System.out.println("(DurabilityCoordinator.setState) last eid in state: " + state.getLastEid());
            getLog().update(state);
            int lastCheckpointEid = state.getLastCheckpointEid();
            lastEid = state.getLastEid();
            bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) I'm going to update myself from EID "
                    + lastCheckpointEid + " to EID " + lastEid);

            stateLock.lock();
            installSnapshot(state.getState());

            for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
                try {
                    bftsmart.tom.util.Logger.println("(DurabilityCoordinator.setState) interpreting and verifying batched requests for eid " + eid);
                    if (state.getMessageBatch(eid) == null) System.out.println("(DurabilityCoordinator.setState) " + eid + " NULO!!!");
                    
                    byte[][] commands = state.getMessageBatch(eid).commands; // take a batch

                    if (commands == null || commands.length <= 0) continue;
                    for(byte[] command : commands)
                    	executeOrdered(command, null);
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
    public StateManager getStateManager() {
    	if(stateManager == null)
    		stateManager = new StandardStateManager();
    	return stateManager;
    }
    
    public abstract void installSnapshot(byte[] state);
    public abstract byte[] getSnapshot();
}
