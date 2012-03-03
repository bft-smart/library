/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import navigators.smart.statemanagment.ApplicationState;
import navigators.smart.tom.MessageContext;
import navigators.smart.tom.util.Logger;

/**
 *
 * @author Joao Sousa
 */
public abstract class DefaultRecoverable implements Recoverable, BatchExecutable {
    
    public static final int CHECKPOINT_PERIOD = 50;

    private ReentrantLock lockState = new ReentrantLock();
    private ReentrantLock hashLock = new ReentrantLock();
    
    private MessageDigest md;
    
    private StateLog log;
    
    public DefaultRecoverable() {
        log = new StateLog(CHECKPOINT_PERIOD);
        try {
            md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (NoSuchAlgorithmException ex) {
            java.util.logging.Logger.getLogger(DefaultRecoverable.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public byte[][] executeBatch(byte[][] commands, MessageContext[] msgCtxs) {
        
        int eid = msgCtxs[0].getConsensusId();
        
        if ((eid > 0) && ((eid % CHECKPOINT_PERIOD) == 0)) {
            Logger.println("(DeliveryThread.run) Performing checkpoint for consensus " + eid);
            saveState(getSnapshot(), eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
        } else {
            Logger.println("(DeliveryThread.run) Storing message batch in the state log for consensus " + eid);
            saveCommands(commands, eid, 0, 0/*tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber())*/);
        }

            
        return executeBatch2(commands, msgCtxs);    }

    
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

        lockState.lock();

        Logger.println("(TOMLayer.saveState) Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.newCheckpoint(snapshot, computeHash(snapshot));
        thisLog.setLastEid(-1);
        thisLog.setLastCheckpointEid(lastEid);
        thisLog.setLastCheckpointRound(decisionRound);
        thisLog.setLastCheckpointLeader(leader);

        lockState.unlock();
        System.out.println("fiz checkpoint");
        System.out.println("tamanho do snapshot: " + snapshot.length);
        System.out.println("tamanho do log: " + thisLog.getMessageBatches().length);
        Logger.println("(TOMLayer.saveState) Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void saveCommands(byte[][] commands, int lastEid, int decisionRound, int leader) {

        StateLog thisLog = getLog();

        lockState.lock();

        Logger.println("(TOMLayer.saveBatch) Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        thisLog.addMessageBatch(commands, decisionRound, leader);
        thisLog.setLastEid(lastEid);

        lockState.unlock();
        
        System.out.println("guardei comandos");
        System.out.println("tamanho do log: " + thisLog.getNumBatches());
        Logger.println("(TOMLayer.saveBatch) Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    @Override
    public ApplicationState getState(int eid, boolean sendState) {
        return (eid > -1 ? getLog().getTransferableState(eid, sendState) : new DefaultApplicationState());
    }
    
    @Override
    public int setState(int recvEid, ApplicationState recvState) {
        
        int lastEid = -1;
        if (recvState instanceof DefaultApplicationState) {
            
            DefaultApplicationState state = (DefaultApplicationState) recvState;
            
            getLog().update(state);
            
            int lastCheckpointEid = state.getLastCheckpointEid();
            //int lastEid = state.getLastEid();
            lastEid = lastCheckpointEid + (state.getMessageBatches() != null ? state.getMessageBatches().length : 0);

            navigators.smart.tom.util.Logger.println("(DeliveryThread.update) I'm going to update myself from EID "
                    + lastCheckpointEid + " to EID " + lastEid);

            installSnapshot(state.getState());

            // INUTIL??????
            //tomLayer.lm.addLeaderInfo(lastCheckpointEid, state.getLastCheckpointRound(),
            //        state.getLastCheckpointLeader());

            for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
                try {
                    byte[][] commands = state.getMessageBatch(eid).commands; // take a batch

                    // INUTIL??????
                    //tomLayer.lm.addLeaderInfo(eid, state.getMessageBatch(eid).round,
                    //        state.getMessageBatch(eid).leader);

                    navigators.smart.tom.util.Logger.println("(DeliveryThread.update) interpreting and verifying batched requests.");

                    //TROCAR POR EXECUTE E ARRAY DE MENSAGENS!!!!!!
                    //TOMMessage[] requests = new BatchReader(batch,
                    //        manager.getStaticConf().getUseSignatures() == 1).deserialiseRequests(manager);
                    executeBatch2(commands, null);
                    
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

        }

        return lastEid;
    }
        
    public abstract void installSnapshot(byte[] state);
    public abstract byte[] getSnapshot();
    public abstract byte[][] executeBatch2(byte[][] commands, MessageContext[] msgCtxs);
}
