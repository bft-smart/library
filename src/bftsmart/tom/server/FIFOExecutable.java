package bftsmart.tom.server;

import bftsmart.tom.MessageContext;

/**
 * @author mhsantos
 * Deliver messages in FIFO order
 *
 */
public interface FIFOExecutable extends SingleExecutable {

    public byte[] executeOrderedFIFO(byte[] command, MessageContext msgCtx, int clientId, int operationId);
    public byte[] executeUnorderedFIFO(byte[] command, MessageContext msgCtx, int clientId, int operationId);

}
