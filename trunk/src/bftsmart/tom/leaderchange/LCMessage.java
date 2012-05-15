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

package bftsmart.tom.leaderchange;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import bftsmart.communication.SystemMessage;
import bftsmart.tom.util.TOMUtil;

/**
 * Message used during leader change and synchronization
 * @author Joao Sousa
 */
public class LCMessage extends SystemMessage {

    private int type;
    private int ts;
    private byte[] payload;
    public final boolean TRIGGER_LC_LOCALLY; // indicates that the replica should
                                             // initiate the LC protocol locally

    /**
     * Empty constructor
     */
    public LCMessage(){
    
        this.TRIGGER_LC_LOCALLY = false;
    }


    /**
     * Constructor
     * @param from replica that creates this message
     * @param type type of the message (STOP, SYNC, CATCH-UP)
     * @param ts timestamp of leader change and synchronization
     * @param payload dada that comes with the message
     */
    public LCMessage(int from, int type, int ts, byte[] payload) {
        super(from);
        this.type = type;
        this.ts = ts;
        this.payload = payload == null ? new byte[0] : payload;
        if (type == TOMUtil.TRIGGER_LC_LOCALLY && from == -1) this.TRIGGER_LC_LOCALLY = true;
        else this.TRIGGER_LC_LOCALLY  = false;
    }

    /**
     * Get type of message
     * @return type of message
     */
    public int getType() {
        return type;
    }

    /**
     * Get timestamp of leader change and synchronization
     * @return timestamp of leader change and synchronization
     */
    public int getReg() {
        return ts;
    }

    /**
     * Obter data of the message
     * @return data of the message
     */
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);

        out.writeInt(type);
        out.writeInt(ts);
        out.writeObject(payload);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);

        type = in.readInt();
        ts = in.readInt();
        payload = (byte[]) in.readObject();
    }
}
