package bftsmart.tom.server;

import bftsmart.tom.MessageContext;

/**
 * 
 * @author mhsantos
 *
 */
public interface Executable {

    /**
     * Method called to execute a request totally ordered.
     * 
     * The message context contains some useful information such as the command
     * sender.
     * 
     * @param command the command issue by the client
     * @param msgCtx information related with the command
     * 
     * @return the reply for the request issued by the client
     */
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx);
}
