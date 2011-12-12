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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import navigators.smart.communication.SystemMessage;
import navigators.smart.tom.util.TOMUtil;


/**
 * This classe represents a message used in the state transfer protocol
 * 
 * @author Jo�o Sousa
 */
public class SMMessage extends SystemMessage implements Externalizable {

    private TransferableState state; // State log
    private int eid; // Execution ID up to which the sender needs to be updated
    private int type; // Message type
    private int replica; // Replica that should send the state
    private int regency; // Current regency
    public final boolean TRIGGER_SM_LOCALLY; // indicates that the replica should
                                             // initiate the SM protocol locally

    /**
     * Constructs a SMMessage
     * @param sender Process Id of the sender
     * @param eid Execution ID up to which the sender needs to be updated
     * @param type Message type
     * @param replica Replica that should send the state
     * @param state State log
     */
    public SMMessage(int sender, int eid, int type, int replica, TransferableState state, int regency) {

        super(sender);
        this.state = state;
        this.eid = eid;
        this.type = type;
        this.replica = replica;
        this.sender = sender;
        this.regency = regency;

        if (type == TOMUtil.TRIGGER_SM_LOCALLY && sender == -1) this.TRIGGER_SM_LOCALLY = true;
        else this.TRIGGER_SM_LOCALLY  = false;

    }

    public SMMessage() {
        this.TRIGGER_SM_LOCALLY = false;
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

    /**
     * Retrieves the execution ID up to which the sender needs to be updated
     * @return The execution ID up to which the sender needs to be updated
     */
    public int getEid() {
        return eid;
    }

    /**
     * Retrieves the replica that should send the state
     * @return The replica that should send the state
     */
    public int getReplica() {
        return replica;
    }

    /**
     * Retrieves the regency that the replica had when sending the state
     * @return The regency that the replica had when sending the state
     */
    public int getRegency() {
        return regency;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);

        out.writeInt(sender);
        out.writeInt(eid);
        out.writeInt(type);
        out.writeInt(replica);
        out.writeInt(regency);
        out.writeObject(state);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);

        sender = in.readInt();
        eid = in.readInt();
        type = in.readInt();
        replica = in.readInt();
        regency = in.readInt();
        state = (TransferableState) in.readObject();
    }
}
