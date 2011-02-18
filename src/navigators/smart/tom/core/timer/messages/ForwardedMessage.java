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

package navigators.smart.tom.core.timer.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * Message used to forward a client request to the current leader when the first
 * timeout for this request is triggered (see RequestTimer).
 *
 */
public final class ForwardedMessage extends SystemMessage {

    private TOMMessage request;

    public ForwardedMessage(ByteBuffer in) throws IOException, ClassNotFoundException {
        super(Type.FORWARDED, in);

        byte[] serReq = SerialisationHelper.readByteArray(in);

        byte[] serReqSign = SerialisationHelper.readByteArray(in);


        request = new TOMMessage(ByteBuffer.wrap(serReq));
        request.setBytes(serReq);
        request.serializedMessageSignature = serReqSign;
    }

    public ForwardedMessage(int senderId, TOMMessage request) {
        super(Type.FORWARDED, senderId);
        this.request = request;
    }

    public TOMMessage getRequest() {
        return request;
    }

    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);

        SerialisationHelper.writeByteArray(request.getBytes(), out);

       SerialisationHelper.writeByteArray(request.serializedMessageSignature, out);
    }

    @Override
    public int getMsgSize(){
    	return super.getMsgSize()+8+request.getBytes().length+request.serializedMessageSignature.length;
    }

}
