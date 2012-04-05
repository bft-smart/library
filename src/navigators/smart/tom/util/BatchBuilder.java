/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.util;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Random;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Batch format: TIMESTAMP(long) + N_NONCES(int) + SEED(long) +
 *               N_MESSAGES(int) + N_MESSAGES*[MSGSIZE(int),MSG(byte),SIG(byte)] +
 *
 *
 * The methods does not try to enforce any constraint, so be correct when using it.
 *
 */
public final class BatchBuilder {

    private Random rnd = new Random();

    /** build buffer */
    private byte[] createBatch(long timestamp, int numberOfNonces, int numberOfMessages, int totalMessagesSize, boolean useSignatures, byte[][] messages, byte[][] signatures, ServerViewManager manager) {
        int size = 20 + //timestamp 8, nonces 4, nummessages 4
                (numberOfNonces > 0 ? 8 : 0) + //seed if needed
                (numberOfMessages*(4+(useSignatures?TOMUtil.getSignatureSize(manager):0)))+ // msglength + signature for each msg
                totalMessagesSize; //size of all msges

        ByteBuffer  proposalBuffer = ByteBuffer.allocate(size);

        proposalBuffer.putLong(timestamp);

        proposalBuffer.putInt(numberOfNonces);

        if(numberOfNonces>0){
            proposalBuffer.putLong(rnd.nextLong());
        }

        proposalBuffer.putInt(numberOfMessages);

        for (int i = 0; i < numberOfMessages; i++) {
            putMessage(proposalBuffer,messages[i], false, signatures[i]);
        }

        return proposalBuffer.array();
    }

    private void putMessage(ByteBuffer proposalBuffer, byte[] message, boolean isHash, byte[] signature) {
        proposalBuffer.putInt(isHash?0:message.length);
        proposalBuffer.put(message);

        if(signature != null) {
            proposalBuffer.put(signature);
        }
    }

        public byte[] makeBatch(Collection<TOMMessage> msgs, int numNounces, long timestamp, ServerViewManager reconfManager) {

        int numMsgs = msgs.size();
        int totalMessageSize = 0; //total size of the messages being batched

        byte[][] messages = new byte[numMsgs][]; //bytes of the message (or its hash)
        byte[][] signatures = new byte[numMsgs][]; //bytes of the message (or its hash)

        // Fill the array of bytes for the messages/signatures being batched
        int i = 0;
        for (TOMMessage msg : msgs) {
            //TOMMessage msg = msgs.next();
            //Logger.println("(TOMLayer.run) adding req " + msg + " to PROPOSE");
            messages[i] = msg.serializedMessage;
            signatures[i] = msg.serializedMessageSignature;

            totalMessageSize += messages[i].length;
            i++;
        }

        // return the batch
        return createBatch(timestamp, numNounces, numMsgs, totalMessageSize,
                reconfManager.getStaticConf().getUseSignatures() == 1, messages, signatures,reconfManager);

    }
}
