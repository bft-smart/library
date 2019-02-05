/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.server.defaultservices.blockchain.logger.ParallelBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.BufferBatchLogger;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.standard.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.core.messages.ForwardedMessage;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.defaultservices.blockchain.logger.AsyncBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.VoidBatchLogger;
import bftsmart.tom.util.BatchBuilder;
import bftsmart.tom.util.TOMUtil;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joao
 */
public abstract class WeakBlockchainRecoverable implements Recoverable, BatchExecutable {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    public String batchDir;    
    
    private TOMConfiguration config;
    private ServerViewController controller;
    private StateManager stateManager;
    private ServerCommunicationSystem commSystem;
    
    private int session;
    private int timeoutSeq;
    private int requestID;
    
    private BatchLogger log;
    private LinkedList<TOMMessage> results;
    
    private int nextNumber;
    private int lastCheckpoint;
    private int lastReconfig;
    private byte[] lastBlockHash;
    
    //private Timer timer;
    private Map<Integer, Set<Integer>> timeouts;
    
    private ReentrantLock timerLock = new ReentrantLock();
        
    public WeakBlockchainRecoverable() {
        
        nextNumber = 0;
        lastCheckpoint = -1;
        lastReconfig = -1;
        lastBlockHash = new byte[] {-1};
        
        results = new LinkedList<>();      
        
        timeouts =  new HashMap<>();
    }
    
    @Override
    public void setReplicaContext(ReplicaContext replicaContext) {

        try {
            
            config = replicaContext.getStaticConfiguration();
            controller = replicaContext.getSVController();
            
            commSystem = replicaContext.getServerCommunicationSystem();
        
            Random rand = new Random(System.nanoTime());
            session = rand.nextInt();
            requestID = 0;
            timeoutSeq = 0;
            
            //batchDir = config.getConfigHome().concat(System.getProperty("file.separator")) +
            batchDir =    "files".concat(System.getProperty("file.separator"));
            
            initLog();
            
            //write genesis block
            byte[] transHash = log.markEndTransactions()[0];
            log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, transHash, new byte[0], lastBlockHash);
            
            log.sync();
                        
            lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, transHash, lastBlockHash);
                        
            nextNumber++;
            
        } catch (Exception ex) {
            
            throw new RuntimeException("Could not set replica context", ex);
        }
        getStateManager().askCurrentConsensusId();
    }

    @Override
    public ApplicationState getState(int cid, boolean sendState) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int setState(ApplicationState state) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StateManager getStateManager() {
        if (stateManager == null) {
            stateManager = new StandardStateManager(); // might need to be other implementation
        }
        return stateManager;
    }

    @Override
    public void Op(int CID, byte[] requests, MessageContext msgCtx) {
        
        // Since we are logging entire batches, we do not use this
    }

    @Override
    public void noOp(int CID, byte[][] operations, MessageContext[] msgCtxs) {
        
        executeBatch(-1, -1, operations, msgCtxs, true);
    }

    @Override
    public TOMMessage[] executeBatch(int processID, int viewID, byte[][] operations, MessageContext[] msgCtxs) {
        
        return executeBatch(processID, viewID, operations, msgCtxs, false);
    }

    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        
        return appExecuteUnordered(command, msgCtx);
    }
    
    private TOMMessage[] executeBatch(int processID, int viewID, byte[][] operations, MessageContext[] msgCtxs, boolean noop) {
        
        int cid = msgCtxs[0].getConsensusId();
        TOMMessage[] replies = new TOMMessage[0];
        boolean timeout = false;
        
        try {
            
            LinkedList<byte[]> transList = new LinkedList<>();
            LinkedList<MessageContext> ctxList = new LinkedList<>(); 
            
            for (int i = 0; i < operations.length ; i++) {
                
                if (controller.isCurrentViewMember(msgCtxs[i].getSender())) {
                                        
                    ByteBuffer buff = ByteBuffer.wrap(operations[i]);
                    
                    int l = buff.getInt();
                    byte[] b = new byte[l];
                    buff.get(b);
                    
                    if ((new String(b)).equals("TIMEOUT")) {
                        
                        int n = buff.getInt();
                        
                        if (n == nextNumber) {
                            
                            logger.info("Got timeout for current block from replica {}!", msgCtxs[i].getSender());
                            
                            Set<Integer> t = timeouts.get(nextNumber);
                            if (t == null) {
                                
                                t = new HashSet<>();
                                timeouts.put(nextNumber, t);
                                
                            }
                            
                            t.add(msgCtxs[i].getSender());
                            
                            if (t.size() >= (controller.getCurrentViewF() + 1)) {
                                
                                timeout = true;
                            }
                        }
                    }
                    
                } else if (!noop) {
                    
                    transList.add(operations[i]);
                    ctxList.add(msgCtxs[i]);
                }
                
            }            
                        
            if (transList.size() > 0) {
                
                byte[][] transApp = new byte[transList.size()][];
                MessageContext[] ctxApp = new MessageContext[ctxList.size()];
                
                transList.toArray(transApp);
                ctxList.toArray(ctxApp);
                
                log.storeTransactions(cid, operations, msgCtxs);
                
                byte[][] resultsApp = executeBatch(transApp, ctxApp);
                //replies = new TOMMessage[results.length];
                
                for (int i = 0; i < resultsApp.length; i++) {
                    
                    TOMMessage reply = getTOMMessage(processID,viewID,transApp[i], ctxApp[i], resultsApp[i]);
                    
                    this.results.add(reply);
                }
                
                /*if (timer != null) timer.cancel();
                timer = new Timer();

                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {

                        logger.info("Timeout for block {}, asking to close it", nextNumber);

                        ByteBuffer buff = ByteBuffer.allocate("TIMEOUT".getBytes().length + (Integer.BYTES * 2));
                        buff.putInt("TIMEOUT".getBytes().length);
                        buff.put("TIMEOUT".getBytes());
                        buff.putInt(nextNumber);

                        sendTimeout(buff.array());
                    }
                    
                }, config.getLogBatchTimeout());*/
            }
            
            boolean isCheckpoint = cid % config.getCheckpointPeriod() == 0;
            
            //if (timeout | isCheckpoint ||  /*(cid % config.getLogBatchLimit() == 0)*/ 
            //        (this.results.size() > config.getMaxBatchSize() * config.getLogBatchLimit())) {
                
                byte[] transHash = log.markEndTransactions()[0];
                
                log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, transHash, new byte[0], lastBlockHash);
                
                lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, transHash, lastBlockHash);
                nextNumber++;
                                
                replies = new TOMMessage[this.results.size()];
                
                this.results.toArray(replies);
                this.results.clear();
                
                if (isCheckpoint) log.clearCached();
                
                logger.info("Synching log at CID " + cid);
                
                log.sync();
                
                timeouts.remove(nextNumber-1);
            //}
            
            return replies;
        } catch (IOException | NoSuchAlgorithmException | InterruptedException ex) {
            logger.error("Error while logging/executing batch for CID " + cid, ex);
            return new TOMMessage[0];
        } 
                
    }
    
    private void sendTimeout(byte[] payload) {
        
        try {
            
            timerLock.lock();
                    
            TOMMessage timeoutMsg = new TOMMessage(config.getProcessId(),
                    session, timeoutSeq, requestID, payload, controller.getCurrentViewId(), TOMMessageType.ORDERED_REQUEST);
            
            byte[] data = serializeTOMMsg(timeoutMsg);
            
            timeoutMsg.serializedMessage = data;
            
            if (config.getUseSignatures() == 1) {
                
                timeoutMsg.serializedMessageSignature = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), data);
                timeoutMsg.signed = true;
                
            }
            
            commSystem.send(controller.getCurrentViewAcceptors(),
                    new ForwardedMessage(this.controller.getStaticConf().getProcessId(), timeoutMsg));
            
            requestID++;
            timeoutSeq++;
            
        } catch (IOException ex) {
            logger.error("Error while sending timeout message.", ex);
        } finally {
            
            timerLock.unlock();
        }
        
    }
    
    private byte[] serializeTOMMsg(TOMMessage msg) throws IOException {
        
        DataOutputStream dos = null;
            byte[] data = null;
            
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dos = new DataOutputStream(baos);
        msg.wExternal(dos);
        dos.flush();
        return baos.toByteArray();
    }
    
    private byte[] computeBlockHash(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] prevBlock) throws NoSuchAlgorithmException {
    
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * 5 + (prevBlock.length + transHash.length));
        
        buff.putInt(number);
        buff.putInt(lastCheckpoint);
        buff.putInt(lastReconf);

        buff.putInt(transHash.length);
        buff.put(transHash);
        
        buff.putInt(prevBlock.length);
        buff.put(prevBlock);

        MessageDigest md = TOMUtil.getHashEngine();
        
        return md.digest(buff.array());
    }
    
    private void initLog() throws FileNotFoundException, NoSuchAlgorithmException{
        
        if (config.getLogBatchType().equalsIgnoreCase("buffer")) {
            log = BufferBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else if(config.getLogBatchType().equalsIgnoreCase("parallel")) {
            log = ParallelBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else if(config.getLogBatchType().equalsIgnoreCase("async")) {
            log = AsyncBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else {
            log = VoidBatchLogger.getInstance(config.getProcessId(), batchDir);
        }
        
    }
    
    public boolean verifyBatch(byte[][] commands, MessageContext[] msgCtxs){
        
        //first off, re-create the batch received in the PROPOSE message of the consensus instance
        int totalMsgsSize = 0;
        byte[][] messages = new byte[commands.length][];
        byte[][] signatures = new byte[commands.length][];
        
        for (int i = 0; i <commands.length; i++) {
            
            TOMMessage msg = msgCtxs[i].recreateTOMMessage(commands[i]);
            messages[i] = msg.serializedMessage;
            signatures[i] = msg.serializedMessageSignature;
            totalMsgsSize += msg.serializedMessage.length;
        }
        
        BatchBuilder builder = new BatchBuilder(0);
        byte[] serializeddBatch = builder.createBatch(msgCtxs[0].getTimestamp(), msgCtxs[0].getNumOfNonces(), msgCtxs[0].getSeed(),
                commands.length, totalMsgsSize, true, messages, signatures);
        
        // now we can obtain the hash contained in the ACCEPT messages from the proposed value
        byte[] hashedBatch = TOMUtil.computeHash(serializeddBatch);
        
        //we are now ready to verify each message that comprises the proof
        int countValid = 0;
        int certificate = (2*controller.getCurrentViewF()) + 1;
        
        HashSet<Integer> alreadyCounted = new HashSet<>();
        
        for (ConsensusMessage consMsg : msgCtxs[0].getProof()) {
            
            ConsensusMessage cm = new ConsensusMessage(consMsg.getType(),consMsg.getNumber(),
                    consMsg.getEpoch(), consMsg.getSender(), consMsg.getValue());
            
            //cm.setCheckpointHash(consMsg.getCheckpointHash());

            ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
            try {
                new ObjectOutputStream(bOut).writeObject(cm);
            } catch (IOException ex) {
                logger.error("Could not serialize message",ex);
            }

            byte[] data = bOut.toByteArray();
                        
            PublicKey pubKey = config.getPublicKey(consMsg.getSender());

            byte[] signature = (byte[]) consMsg.getProof();

            if (Arrays.equals(consMsg.getValue(), hashedBatch) &&
                    TOMUtil.verifySignature(pubKey, data, signature) && !alreadyCounted.contains(consMsg.getSender())) {

                alreadyCounted.add(consMsg.getSender());
                countValid++;
            } else {
                logger.error("Invalid signature in message from " + consMsg.getSender());
            }
        }
        
        boolean ret = countValid >=  certificate;
        logger.info("Proof for CID {} is {} ({} valid messages, needed {})",
                msgCtxs[0].getConsensusId(), (ret ? "valid" : "invalid"), countValid, certificate);
        return ret;
    }
    
    @Override
    public byte[][] executeBatch(byte[][] operations, MessageContext[] msgCtxs) {

        return appExecuteBatch(operations, msgCtxs, true);
    }
 
    /**
     * Given a snapshot received from the state transfer protocol, install it
     * @param state The serialized snapshot
     */
    public abstract void installSnapshot(byte[] state);
    
    /**
     * Returns a serialized snapshot of the application state
     * @return A serialized snapshot of the application state
     */
    public abstract byte[] getSnapshot();
    
    /**
     * Execute a batch of ordered requests
     * 
     * @param commands The batch of requests
     * @param msgCtxs The context associated to each request
     * @param fromConsensus true if the request arrived from a consensus execution, false if it arrives from the state transfer protocol
     * 
     * @return the respective replies for each request
     */
    public abstract byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus);
    
    /**
     * Execute an unordered request
     * 
     * @param command The unordered request
     * @param msgCtx The context associated to the request
     * 
     * @return the reply for the request issued by the client
     */
    public abstract byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx);
}
