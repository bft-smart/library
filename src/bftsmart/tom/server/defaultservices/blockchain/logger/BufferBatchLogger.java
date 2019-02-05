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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joao
 */
public class BufferBatchLogger implements BatchLogger {
    
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
    
    private LinkedList<ByteBuffer> buffers;
    
    private BufferBatchLogger() {
        //not to be used
        
    }
    
    private BufferBatchLogger(int id, String logDir) throws FileNotFoundException, NoSuchAlgorithmException {
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
        
        buffers = new LinkedList<>();
        
        logger.info("Buffer batch logger instantiated");

    }
    
    public static BatchLogger getInstance(int id, String logDir) throws FileNotFoundException, NoSuchAlgorithmException {
        BufferBatchLogger ret = new BufferBatchLogger(id, logDir);
        return ret;
    }
    
    public void storeTransactions(int cid, byte[][] requests, MessageContext[] contexts) throws IOException {
        
        if (firstCachedCID == -1) firstCachedCID = cid;
        lastCachedCID = cid;
        CommandsInfo cmds = new CommandsInfo(requests, contexts);
        cachedBatches.add(cmds);
        writeTransactionsToDisk(cid, cmds);
        
    }
    
    public void storeResults(byte[][] results) throws IOException{
     
        cachedResults.add(results);
        writeResultsToDisk(results);
    }
    
    public byte[][] markEndTransactions() throws IOException {
        
        ByteBuffer buff = getEOT();
        
        //channel.write(buff);
        buffers.add(buff);
        return new byte[][] {transDigest.digest(), resultsDigest.digest()};
    }
    
    public void storeHeader(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] resultsHash,  byte[] prevBlock) throws IOException {
     
        logger.debug("writting header for block #{} to disk", number);
        
        ByteBuffer buff = prepareHeader(number, lastCheckpoint, lastReconf, transHash, resultsHash, prevBlock);
                
        //channel.write(buff);
        buffers.add(buff);
        
        logger.debug("wrote header for block #{} to disk", number);
    }
    
    public void storeCertificate(Map<Integer, byte[]> sigs) throws IOException {
        
        logger.debug("writting certificate to disk");
        
        ByteBuffer buff = prepareCertificate(sigs);
        
        //channel.write(buff);
        buffers.add(buff);
        
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
    
    private void writeTransactionsToDisk(int cid, CommandsInfo commandsInfo) throws IOException {
        
        logger.debug("writting transactios to disk");
        
        byte[] transBytes = serializeTransactions(commandsInfo);
        
        //update the transactions hash for the entire block
        transDigest.update(transBytes);

        ByteBuffer buff = prepareTransactions(cid, transBytes);
        
        //channel.write(buff);
        buffers.add(buff);
        
        logger.debug("wrote transactions to disk");

    }
    
    private void writeResultsToDisk(byte[][] results) throws IOException {
        
        logger.debug("writting results to disk");
        
        for (byte[] result : results) { //update the results hash for the entire block
        
            resultsDigest.update(result);

        }
        
        ByteBuffer buff = prepareResults(results);
        
        //channel.write(buff);
        buffers.add(buff);
        
        logger.debug("wrote results to disk");

    }
    
    public void sync() throws IOException {
        
        logger.debug("synching log to disk");

        //log.getFD().sync();
        //channel.force(false);
        
        ByteBuffer[] bbs = new ByteBuffer[buffers.size()];
        buffers.toArray(bbs);

        //log.write(serializeByteBuffers(bbs));
        channel.write(bbs);
        channel.force(false);
        
        logger.debug("synced log to disk");
    }
    

}

