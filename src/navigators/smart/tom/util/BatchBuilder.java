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

/**
 * Batch format: N_MESSAGES(int) + N_MESSAGES*[MSGSIZE(int),MSG(byte),SIG(byte)] +
 *               TIMESTAMP(long) + N_NONCES(int) + NONCES(byte[])
 *
 * The methods does not try to enforce any constraint, so be correct when using it.
 *
 */
public final class BatchBuilder {

    private ByteBuffer proposalBuffer;

    /** build buffer */
    public BatchBuilder(int numberOfMessages, int numberOfNonces, int totalMessagesSize, boolean useSignatures) {

        this.proposalBuffer = ByteBuffer.allocate(16+
                (numberOfMessages*(4+(useSignatures?TOMUtil.getSignatureSize():0)))+
                totalMessagesSize+numberOfNonces);

        this.proposalBuffer.putInt(numberOfMessages);
    }

    /** 1 */
    public void putMessage(byte[] message, boolean isHash, byte[] signature) {
        proposalBuffer.putInt(isHash?0:message.length);
        proposalBuffer.put(message);

        if(signature != null) {
            proposalBuffer.put(signature);
        }
    }

    /** 2 */
    public void putTimestamp(long timestamp) {
        proposalBuffer.putLong(timestamp);
    }

    /** 3 */
    public void putNonces(byte[] nonces) {
        proposalBuffer.putInt(nonces.length);
        proposalBuffer.put(nonces);
    }

    public byte[] getByteArray() {
        return proposalBuffer.array();
    }
}
