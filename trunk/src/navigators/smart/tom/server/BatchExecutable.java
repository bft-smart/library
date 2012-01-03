package navigators.smart.tom.server;

import navigators.smart.tom.MessageContext;

/**
 * 
 * @author mhsantos
 *
 */
public interface BatchExecutable extends Executable {
	
	/**
	 * Execute a batch of requests.
	 * @param command
	 * @param msgCtx
	 * @return
	 */
    public byte[][] executeBatch(byte[][] command, MessageContext msgCtx);

}
