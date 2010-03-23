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
import java.io.ObjectInput;
import java.io.ObjectOutput;

import navigators.smart.tom.core.messages.SystemMessage;


/**
 * This class represents a message used in the TO-FREEZE fase
 * 
 */
public class RTMessage extends SystemMessage {
   
    private int rtType; // message type (RT_TIMEOUT, RT_COLLECT, RT_LEADER)
    private int reqId; // Request ID associated with the timeout
    private Object content; // content of this message. Varies according to the message type

    /**
     * Creates a new instance of RequestTimeoutMessage
     */
    public RTMessage(){}

    /**
     * Creates a new instance of RequestTimeoutMessage
     * @param rtType Message type (RT_TIMEOUT, RT_COLLECT, RT_LEADER)
     * @param reqId Request ID associated with the timeout
     * @param timeout Timeout Number
     * @param from Replica ID of the sender
     * @param content Content of this message. Varies according to the message type
     */
    public RTMessage(int rtType, int reqId, int from, Object content) {
        super(from);
        this.rtType = rtType;
        this.reqId = reqId;
        this.content = content;
    }

    /**
     * Retrieves the message type
     * @return The message type
     */
    public int getRTType(){
        return this.rtType;
    }

    /**
     * Retrieves the request ID associated with the timeout
     * @return The request ID associated with the timeout
     */
    public int getReqId(){
        return this.reqId;
    }

    /**
     * Retrieves the content of this message
     * @return  The content of this message
     */
    public Object getContent(){
        return this.content;
    }

    // overwritten methods from the super-class

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);

        out.writeInt(rtType);
        out.writeInt(reqId);
        out.writeObject(content);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);

        rtType = in.readInt();
        reqId = in.readInt();
        content = in.readObject();
    }
}
