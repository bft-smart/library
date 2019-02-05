/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain.logger;

import bftsmart.tom.MessageContext;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.blockchain.BatchLogger;
import bftsmart.tom.util.TOMUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joao
 */
public class ParallelBatchLogger extends Thread implements BatchLogger {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private int id;
    private int lastCachedCID = -1;
    private int firstCachedCID = -1;
    private LinkedList<CommandsInfo> cachedBatches;
    private LinkedList<byte[][]> cachedResults;
    private RandomAccessFile log;
    private FileChannel channel;
    private String logPath;
    private MessageDigest transDigest;
    private MessageDigest resultsDigest;
    
    private LinkedBlockingQueue<ByteBuffer> buffers;
    private boolean synched = false;
    
    private ReentrantLock syncLock = new ReentrantLock();
    private Condition isSynched = syncLock.newCondition();
    
    private final Lock queueLock = new ReentrantLock();
    private final Condition notEmptyQueue = queueLock.newCondition();
    
    private ParallelBatchLogger() {
        //not to be used
        
    }
    
    private ParallelBatchLogger(int id, String logDir) throws FileNotFoundException, NoSuchAlgorithmException {
        this.id = id;
        cachedBatches = new LinkedList<>();
        cachedResults = new LinkedList<>();
        
        File directory = new File(logDir);
        if (!directory.exists()) directory.mkdir();
        
        logPath = logDir + String.valueOf(this.id) + "." + System.currentTimeMillis() + ".log";
        
        logger.debug("Logging to file " + logPath);
        log = new RandomAccessFile(logPath, "rwd");
        channel = log.getChannel();
         
        transDigest = TOMUtil.getHashEngine();
        resultsDigest = TOMUtil.getHashEngine();
        
        buffers = new LinkedBlockingQueue<>();

        logger.info("Parallel batch logger instantiated");

    }
    
    public static BatchLogger getInstance(int id, String logDir) throws FileNotFoundException, NoSuchAlgorithmException {
        ParallelBatchLogger ret = new ParallelBatchLogger(id, logDir);
        ret.start();        
        return ret;
    }
    
    public void storeTransactions(int cid, byte[][] requests, MessageContext[] contexts) throws IOException, InterruptedException {
        
        if (firstCachedCID == -1) firstCachedCID = cid;
        lastCachedCID = cid;
        CommandsInfo cmds = new CommandsInfo(requests, contexts);
        cachedBatches.add(cmds);
        writeTransactionsToDisk(cid, cmds);
        
    }
    
    public void storeResults(byte[][] results) throws IOException, InterruptedException {
     
        cachedResults.add(results);
        writeResultsToDisk(results);
    }
    
    public byte[][] markEndTransactions() throws IOException, InterruptedException {
        
        ByteBuffer buff = getEOT();
        
        //channel.write(buff);
        enqueueWrite(buff);
        
        return new byte[][] {transDigest.digest(), resultsDigest.digest()};
    }
    
    public void storeHeader(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] resultsHash,  byte[] prevBlock) throws IOException, InterruptedException {
     
        logger.debug("writting header for block #{} to disk", number);
        
        ByteBuffer buff = prepareHeader(number, lastCheckpoint, lastReconf, transHash, resultsHash, prevBlock);
                
        //channel.write(buff);
        enqueueWrite(buff);
        
        logger.debug("wrote header for block #{} to disk", number);
    }
    
    public void storeCertificate(Map<Integer, byte[]> sigs) throws IOException, InterruptedException {
        
        logger.debug("writting certificate to disk");
        
        ByteBuffer buff = prepareCertificate(sigs);
        
        //channel.write(buff);
        enqueueWrite(buff);
        
        logger.debug("wrote certificate to disk");
    }
    
    public int getLastCachedCID() {
        return lastCachedCID;
    }

    public int getFirstCachedCID() {
        return firstCachedCID;
    }
    
    public CommandsInfo[] getCached() {
        
        CommandsInfo[] cmds = new CommandsInfo[cachedBatches.size()];
        cachedBatches.toArray(cmds);
        return cmds;
        
    }
    public void clearCached() {
        
        cachedBatches.clear();
        firstCachedCID = -1;
        lastCachedCID = -1;
    }
    
    private void writeTransactionsToDisk(int cid, CommandsInfo commandsInfo) throws IOException, InterruptedException {
        
        logger.debug("writting transactios to disk");
        
        byte[] transBytes = serializeTransactions(commandsInfo);
                
        //update the transactions hash for the entire block
        transDigest.update(transBytes);
        
        ByteBuffer buff = prepareTransactions(cid, transBytes);
        
        //channel.write(buff);
        enqueueWrite(buff);
        
        logger.debug("wrote transactions to disk");

    }
    
    private void writeResultsToDisk(byte[][] results) throws IOException, InterruptedException {
        
        logger.debug("writting results to disk");
        
        for (byte[] result : results) { //update the results hash for the entire block
        
            resultsDigest.update(result);

        }
        
        ByteBuffer buff = prepareResults(results);
        
        //channel.write(buff);
        enqueueWrite(buff);
        
        logger.debug("wrote results to disk");

    }
    
    public void sync() throws IOException, InterruptedException {
        
        logger.debug("synching log to disk");

        //log.getFD().sync();
        //channel.force(false);
        
        //ByteBuffer[] bbs = new ByteBuffer[buffers.size()];
        //buffers.toArray(bbs);
        //channel.write(bbs);
        
        ByteBuffer eob = ByteBuffer.allocate(0);
        enqueueWrite(eob);
        
        syncLock.lock();
        while (!synched) {
            
            isSynched.await(10, TimeUnit.MILLISECONDS);
        }
        synched = false;
        syncLock.unlock();
        
        logger.debug("synced log to disk");
    }
    
    private void enqueueWrite(ByteBuffer buffer) throws InterruptedException {
        
        queueLock.lock();
        buffers.put(buffer);
        notEmptyQueue.signalAll();
        queueLock.unlock();
    }
    
    public void run () {
        
        while (true) {
            
            
            try {
                
                LinkedList<ByteBuffer> list = new LinkedList<>();
                
                queueLock.lock();
                while (buffers.isEmpty()) notEmptyQueue.await(10, TimeUnit.MILLISECONDS);
                buffers.drainTo(list);
                queueLock.unlock();
                
                logger.debug("Drained buffers");
                
                ByteBuffer[] array = new ByteBuffer[list.size()];
                list.toArray(array);
                
                    //log.write(serializeByteBuffers(array));
                    channel.write(array);
                    channel.force(false);
                    
                if (array[array.length-1].capacity() == 0) {
                    
                    logger.debug("I was told to sync");
                                    
                    syncLock.lock();
                    
                    synched = true;
                    isSynched.signalAll();

                    syncLock.unlock();
                }
                
            } catch (IOException | InterruptedException ex) {
                
                logger.error("Could not write to disk", ex);
            } 
        }
    }
}
