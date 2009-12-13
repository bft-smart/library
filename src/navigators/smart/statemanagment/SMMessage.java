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

package navigators.smart.statemanagment;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import navigators.smart.tom.core.messages.SystemMessage;


/**
 * This classe represents a message used in the state transfer protocol
 * 
 * @author João Sousa
 */
public class SMMessage extends SystemMessage {

    private TransferableState state; // State log
    private int type; // Message type

    /**
     * Constructs a SMMessage
     * @param sender Process Id of the sender
     * @param type Message type
     * @param state State log
     */
    public SMMessage(int sender, int type, TransferableState state) {

        super(sender);
        this.state = state;
        this.type = type;
        this.sender = sender;

    }

    /**
     * Retrieves the state log
     * @return The state Log
     */
    public TransferableState getState() {
        return state;
    }

    /**
     * Retrieves the type of the message
     * @return The type of the message
     */
    public int getType() {
        return type;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);

        out.writeInt(sender);
        out.writeInt(type);
        out.writeObject(state);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);

        sender = in.readInt();
        type = in.readInt();
        state = (TransferableState) in.readObject();
    }
}
