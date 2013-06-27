package bftsmart.statemanagement.strategy;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import bftsmart.reconfiguration.views.View;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.SMMessage;

public class StandardSMMessage extends SMMessage {

	private int replica;

    public StandardSMMessage(int sender, int eid, int type, int replica, ApplicationState state, View view, int regency, int leader) {
    	super(sender, eid, type, state, view, regency, leader);
    	this.replica = replica;
    }
	
    public StandardSMMessage() {
    	super();
    }
    
    /**
     * Retrieves the replica that should send the state
     * @return The replica that should send the state
     */
    public int getReplica() {
        return replica;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);
        out.writeInt(replica);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);
        replica = in.readInt();
    }
}
