/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import navigators.smart.tom.core.messages.SystemMessage;

/**
 *
 * @author eduardo
 */
public class TTPMessage extends SystemMessage{
    private ReconfigureReply reply;
    
    public TTPMessage(){}
    
    public TTPMessage(ReconfigureReply reply){
        super();
        this.reply = reply;
    }
    
     public TTPMessage(int from, ReconfigureReply reply){
         super(from);
         this.reply = reply;
    }
     
     
      // Implemented method of the Externalizable interface
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(reply);
    }

    // Implemented method of the Externalizable interface
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.reply = (ReconfigureReply) in.readObject();
    }

    public ReconfigureReply getReply() {
        return reply;
    }
}
