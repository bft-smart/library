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

package bftsmart.tom.core.timer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import bftsmart.communication.SystemMessage;
import bftsmart.tom.core.messages.TOMMessage;



/**
 * Message used to forward a client request to the current leader when the first
 * timeout for this request is triggered (see RequestTimer).
 *
 */
public final class ForwardedMessage extends SystemMessage {

    private TOMMessage request;

    public ForwardedMessage() {
    }

    public ForwardedMessage(int senderId, TOMMessage request) {
        super(senderId);
        this.request = request;
    }

    public TOMMessage getRequest() {
        return request;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(request.serializedMessage.length);
        out.write(request.serializedMessage);
        out.writeBoolean(request.signed);

        if (request.signed) {
            out.writeInt(request.serializedMessageSignature.length);
            out.write(request.serializedMessageSignature);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        byte[] serReq = new byte[in.readInt()];
        in.readFully(serReq);

        request = TOMMessage.bytesToMessage(serReq);
        request.serializedMessage = serReq;

        boolean signed = in.readBoolean();

        if (signed) {

            byte[] serReqSign = new byte[in.readInt()];
            in.readFully(serReqSign);
            request.serializedMessageSignature = serReqSign;

        }
    }

}
