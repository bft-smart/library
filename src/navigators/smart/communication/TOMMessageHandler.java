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

package navigators.smart.communication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;
import navigators.smart.tom.util.TOMUtil;


/**
 * @author edualchieri
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class TOMMessageHandler implements MessageHandler<SystemMessage,byte[]> {

    private final static Logger log = Logger.getLogger(TOMMessageHandler.class.getCanonicalName());

    private final TOMLayer tomLayer;
   
    public TOMMessageHandler(TOMLayer tomlayer){
        this.tomLayer = tomlayer;
    }

    @Override
    public void processData(SystemMessage sm) {
       if (sm instanceof ForwardedMessage) {
            TOMMessage request = ((ForwardedMessage) sm).getRequest();
            if(log.isLoggable(Level.FINER))
                log.finer("(MessageHandler.processData) receiving: " + request);
            tomLayer.requestReceived(request);
            //got Statetransfer Message
        } else if (sm instanceof SMMessage) {
            if(log.isLoggable(Level.FINER))
                log.finer("(MessageHandler.processData) receiving a state managment message from replica " + sm.getSender());
            SMMessage smsg = (SMMessage) sm;
            if (smsg.getType() == TOMUtil.SM_REQUEST) {
                tomLayer.SMRequestDeliver(smsg);
            }
            else {
                tomLayer.SMReplyDeliver(smsg);
            }
        }
    }

    public byte[] getData(SystemMessage msg) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        try {
            ObjectOutputStream obOut = new ObjectOutputStream(bOut);

            obOut.writeObject(msg);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return bOut.toByteArray();
    }

    public SystemMessage deserialise(SystemMessage.Type type, ByteBuffer buf, byte[] verificationresult) throws IOException, ClassNotFoundException {
    	
        switch(type){
            case FORWARDED:
                return new ForwardedMessage(buf);
            case SM_MSG:
                return new SMMessage(buf);
            default:
                log.warning("Received msg for unknown msg type");
                return null;
        }
    }
    }
