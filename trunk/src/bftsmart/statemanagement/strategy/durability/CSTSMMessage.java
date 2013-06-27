package bftsmart.statemanagement.strategy.durability;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import bftsmart.reconfiguration.views.View;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.SMMessage;

public class CSTSMMessage extends SMMessage {

	private CSTRequestF1 cstConfig;
	
    public CSTSMMessage(int sender, int eid, int type, CSTRequestF1 cstConfig, ApplicationState state, View view, int regency, int leader) {
    	super(sender, eid, type, state, view, regency, leader);
    	this.cstConfig = cstConfig;
    }
    
    public CSTSMMessage() {
    	super();
    }

    public CSTRequestF1 getCstConfig() {
    	return cstConfig;
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);
        out.writeObject(cstConfig);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);
        cstConfig = (CSTRequestF1)in.readObject();
    }
	
}
