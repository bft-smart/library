package bftsmart.demo.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;

/**
 * Debug server. 
 */
public final class ThroughputDebugServer extends DefaultRecoverable{
    
    private int replySize;
    private boolean context;
    private byte[] state;
    private static Logger logger;
    
    private ServiceReplica replica;

    public ThroughputDebugServer(int id, int replySize, int stateSize, boolean context) {

    	logger = LoggerFactory.getLogger(ThroughputDebugServer.class);
    	
        this.replySize = replySize;
        this.context = context;
        this.state = new byte[stateSize];
        
        for (int i = 0; i < stateSize ;i++)
            state[i] = (byte) i;
        
        replica = new ServiceReplica(id, this, this);
    }
    
    @Override
    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {
        
        byte[][] replies = new byte[commands.length][];
        
        for (int i = 0; i < commands.length; i++) {
            
            replies[i] = execute(commands[i],msgCtxs[i]);
            
        }
        
        return replies;
    }
    
    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    public byte[] execute(byte[] command, MessageContext msgCtx) {        
        boolean readOnly = false;
        
        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {
        	
            readOnly = msgCtx.readOnly;
            
            msgCtx.getFirstInBatch().executedTime = System.nanoTime();
            
          //logger.info("Executing command for cId:" + msgCtx.getConsensusId());
            
        }
        logger.debug("Command executed for cId:" + msgCtx.getConsensusId());
    
        return new byte[replySize];
    }

    public static void main(String[] args){
        if(args.length < 4) {
            logger.info("Usage: ... ThroughputLatencyServer <processId> <reply size> <state size> <context?>");
            System.exit(-1);
        }
        int processId = Integer.parseInt(args[0]);
        int replySize = Integer.parseInt(args[1]);
        int stateSize = Integer.parseInt(args[2]);
        boolean context = Boolean.parseBoolean(args[3]);
        new ThroughputDebugServer(processId, replySize, stateSize, context);        
    }

    @Override
    public void installSnapshot(byte[] state) {
        //nothing
    }

    @Override
    public byte[] getSnapshot() {
        return this.state;
    }

   
}
