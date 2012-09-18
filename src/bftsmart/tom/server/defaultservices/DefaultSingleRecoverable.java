package bftsmart.tom.server.defaultservices;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bftsmart.statemanagment.ApplicationState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.SingleExecutable;
import bftsmart.tom.util.Logger;

/**
 *
 * @author mhsantos
 */
public abstract class DefaultSingleRecoverable implements Recoverable, SingleExecutable {
    
    public static final int CHECKPOINT_PERIOD = 50;

    private ReentrantLock logLock = new ReentrantLock();
    private ReentrantLock hashLock = new ReentrantLock();
    private ReentrantLock stateLock = new ReentrantLock();
    
    private MessageDigest md;
        
    private StateLog log;
    private int messageCounter;
    private byte[][] commands;
    
    public DefaultSingleRecoverable() {

        try {
            md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (NoSuchAlgorithmException ex) {
            java.util.logging.Logger.getLogger(DefaultSingleRecoverable.class.getName()).log(Level.SEVERE, null, ex);
        }
        byte[] state = getSnapshot();
        log = new StateLog(CHECKPOINT_PERIOD, state, computeHash(state));
    }
    
    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        
        int eid = msgCtx.getConsensusId();
            
        stateLock.lock();
        byte[] reply = appExecuteOrdered(command, msgCtx);
        stateLock.unlock();

        if(messageCounter == 0) { //first message of the batch
        	commands = new byte[msgCtx.getBatchSize()][];
        }
        commands[messageCounter] = command;
        messageCounter++;
        
        if(messageCounter == msgCtx.getBatchSize()) {
	        if ((eid > 0) && ((eid % CHECKPOINT_PERIOD) == 0)) {
	            Logger.println("(DefaultRecoverable.executeBatch) Performing checkpoint for consensus " + eid);
	            stateLock.lock();
	            byte[] snapshot = getSnapshot();
	            stateLock.unlock();
	            saveState(snapshot, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
	        } else {
	            Logger.println("(DefaultRecoverable.executeBatch) Storing message batch in the state log for consensus " + eid);
	            saveCommands(commands, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
	        }
	        messageCounter = 0;
        }
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
    public void saveState(byte[] snapshot, int lastEid, int decisionRound, int leader) {

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

    public void saveCommands(byte[][] commands, int lastEid, int decisionRound, int leader) {
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
            
            System.out.println("(DefaultRecoverable.setState) last eid in state: " + state.getLastEid());
            
            getLog().update(state);
            
            int lastCheckpointEid = state.getLastCheckpointEid();
            
            lastEid = state.getLastEid();
            //lastEid = lastCheckpointEid + (state.getMessageBatches() != null ? state.getMessageBatches().length : 0);

            bftsmart.tom.util.Logger.println("(DefaultRecoverable.setState) I'm going to update myself from EID "
                    + lastCheckpointEid + " to EID " + lastEid);

            stateLock.lock();
            installSnapshot(state.getState());

            // INUTIL??????
            //tomLayer.lm.addLeaderInfo(lastCheckpointEid, state.getLastCheckpointRound(),
            //        state.getLastCheckpointLeader());

            for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
                try {
                    bftsmart.tom.util.Logger.println("(DefaultRecoverable.setState) interpreting and verifying batched requests for eid " + eid);
                    if (state.getMessageBatch(eid) == null) System.out.println("(DefaultRecoverable.setState) " + eid + " NULO!!!");
                    
                    byte[][] commands = state.getMessageBatch(eid).commands; // take a batch

                    if (commands == null || commands.length <= 0) continue;
                    // INUTIL??????
                    //tomLayer.lm.addLeaderInfo(eid, state.getMessageBatch(eid).round,
                    //        state.getMessageBatch(eid).leader);

                    //TROCAR POR EXECUTE E ARRAY DE MENSAGENS!!!!!!
                    //TOMMessage[] requests = new BatchReader(batch,
                    //        manager.getStaticConf().getUseSignatures() == 1).deserialiseRequests(manager);
                    for(byte[] command : commands)
                    	appExecuteOrdered(command, null);
                    
                    // ISTO E UM PROB A RESOLVER!!!!!!!!!!!!
                    //tomLayer.clientsManager.requestsOrdered(requests);

                    // INUTIL??????
                    //deliverMessages(eid, tomLayer.getLCManager().getLastReg(), false, requests, batch);

                    // IST E UM PROB A RESOLVER!!!!
                    //******* EDUARDO BEGIN **************//
                    /*if (manager.hasUpdates()) {
                        processReconfigMessages(lastCheckpointEid, state.getLastCheckpointRound());
                    }*/
                    //******* EDUARDO END **************//
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
        
    public abstract void installSnapshot(byte[] state);
    public abstract byte[] getSnapshot();
    public abstract byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx);
}
