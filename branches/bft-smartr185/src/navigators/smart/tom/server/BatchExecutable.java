package navigators.smart.tom.server;

import navigators.smart.tom.MessageContext;

/**
 * Interface to implement an object that executes a batch of commands
 * @author mhsantos
 *
 */
public interface BatchExecutable extends Executable {
	
    /**
     * Method called to execute a request totally ordered.
     * 
     * The message context contains some useful information such as the command
     * sender.
     * @param command Ordered commands to be executed
     * @param msgCtx Message contexts for each command
     * @return Replies for each clients
     */
    public byte[][] executeBatch(byte[][] command, MessageContext[] msgCtx);

}
