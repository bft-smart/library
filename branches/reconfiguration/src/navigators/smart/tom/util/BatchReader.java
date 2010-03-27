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
 * Batch format: N_MESSAGES(int) + N_MESSAGES*[MSGSIZE(int),MSG(byte)] +
 *               TIMESTAMP(long) + N_NONCES(int) + NONCES(byte[])
 *
 */
public final class BatchReader {

    private ByteBuffer proposalBuffer;
    private boolean useSignatures;

    /** wrap buffer */
    public BatchReader(byte[] batch, boolean useSignatures) {
        proposalBuffer = ByteBuffer.wrap(batch);
        this.useSignatures = useSignatures;
    }

    /** 1 */
    public int getNumberOfMessages() {
        return proposalBuffer.getInt();
    }

    /** 2 */
    public int getNextMessageSize() {
        return proposalBuffer.getInt();
    }

    /** 3 */
    public void getNextMessage(byte[] message) {
        proposalBuffer.get(message);
    }

    /** 4 */
    public void getNextSignature(byte[] signature) {
        proposalBuffer.get(signature);
    }

    /** 5 */
    public long getTimestamp() {
        return proposalBuffer.getLong();
    }

    /** 6 */
    public int getNumberOfNonces() {
        return proposalBuffer.getInt();
    }

    /** 7 */
    public void getNonces(byte[] nonces) {
        proposalBuffer.get(nonces);
    }

    public void skipMessages() {
        int numberOfMessages = getNumberOfMessages();
        int signatureSize = TOMUtil.getSignatureSize();

        for(int i=0; i<numberOfMessages; i++) {
            int messageSize = getNextMessageSize();
            proposalBuffer.position(proposalBuffer.position()+messageSize+(useSignatures?signatureSize:0));
        }
    }
}
