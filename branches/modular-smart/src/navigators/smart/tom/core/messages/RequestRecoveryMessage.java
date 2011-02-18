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

package navigators.smart.tom.core.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.util.SerialisationHelper;
import navigators.smart.tom.util.TOMUtil;


/**
 * This class represents a message used when recovering requests
 *
 */
public class RequestRecoveryMessage extends SystemMessage {

    //TODO: Nao faz mais sentido chamar a isto "type" ?
    private int id; // Message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
    private int consId; // Consensus's ID to which the request recover refers to
    private byte[] hash; // Hash of the request being recovered
    private TOMMessage msg; // TOM message containing the request being recovered

    /**
     * Creates a new instance of RecoveryRequestMessage
     */
    public RequestRecoveryMessage(ByteBuffer in) throws IOException, ClassNotFoundException {
        super(Type.RR_MSG,in);
        consId = in.getInt();
        id = in.getInt();
        hash = SerialisationHelper.readByteArray(in);

        msg = new TOMMessage(in);
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_REQUEST type
     * @param hash Hash of the request being recovered
     * @param from ID of the process which sent the message
     */
    public RequestRecoveryMessage(byte[] hash, int from) {

        super(Type.RR_MSG,from);
        this.hash = hash;
        this.id = TOMUtil.RR_REQUEST;
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_REPLY type
     * @param msg TOM message containing the request being recovered
     * @param from ID of the process which sent the message
     */
    public RequestRecoveryMessage(TOMMessage msg, int from) {

        super(Type.RR_MSG,from);
        this.msg = msg;
        this.id = TOMUtil.RR_REPLY;
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_DELIVERED type
     * @param hash Hash of the request being recovered
     * @param from ID of the process which sent the message
     * @param consId  Consensus's ID to which the request recover refers to
     */
    public RequestRecoveryMessage(byte[] hash, int from, int consId) {
        super(Type.RR_MSG,from);

        this.hash = hash;
        this.id = TOMUtil.RR_DELIVERED;
        this.consId = consId;
    }

    /**
     * Retrieves the consensus's ID to which the request recover refers to
     * @return The consensus's ID to which the request recover refers to
     */
    public int getConsId() {
        return this.consId;
    }

    /**
     * Retrieves the message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
     * @return The message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
     */
    public int getId() {
        return this.id;
    }

    /**
     * Retrieves the TOM message containing the request being recovered
     * @return The TOM message containing the request being recovered
     */
    public TOMMessage getMsg() {
        return this.msg;
    }

    /**
     * Retrieves the hash of the request being recovered. If there is no hash the length is 0
     * @return The hash of the request being recovered
     */
    public byte[] getHash() {
        return this.hash;
    }

    // The following are overwritten methods

    @Override
    public void serialise(ByteBuffer out) {

        super.serialise(out);
        out.putInt(consId);
        out.putInt(id);

        SerialisationHelper.writeByteArray(hash, out);

        msg.serialise(out);
    }
    
    

    @Override
    public String toString() {
        return "consId=" + getConsId() + ", type=" + getId() + ", from=" + getSender();
    }

	/* (non-Javadoc)
	 * @see navigators.smart.tom.core.messages.SystemMessage#getMsgSize()
	 */
	@Override
	public int getMsgSize() {
		return super.getMsgSize() + 12 + hash.length+msg.getMsgSize();
	}
}