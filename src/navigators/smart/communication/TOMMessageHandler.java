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

import java.io.ObjectInput;
import static navigators.smart.communication.ServerCommunicationSystem.RR_MSG;
import static navigators.smart.communication.ServerCommunicationSystem.RT_MSG;
import static navigators.smart.communication.ServerCommunicationSystem.TOM_REPLY_MSG;
import static navigators.smart.communication.ServerCommunicationSystem.TOM_REQUEST_MSG;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutputStream;

import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.tom.util.SerialisationHelper;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;


/**
 * @author edualchieri
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class TOMMessageHandler implements MessageHandler<byte[]> {

    private final TOMLayer tomLayer;
   
    public TOMMessageHandler(TOMLayer tomlayer){
        this.tomLayer = tomlayer;
    }

    @Override
    public void processData(SystemMessage sm) {
       if (sm instanceof ForwardedMessage) {
            TOMMessage request = ((ForwardedMessage) sm).getRequest();
            Logger.println("(MessageHandler.processData) receiving: " + request);
            tomLayer.requestReceived(request);
        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
        } else if (sm instanceof SMMessage) {

            Logger.println("(MessageHandler.processData) receiving a state managment message from replica " + sm.getSender());
            SMMessage smsg = (SMMessage) sm;
            if (smsg.getType() == TOMUtil.SM_REQUEST) {
                tomLayer.SMRequestDeliver(smsg);
            }
            else {
                tomLayer.SMReplyDeliver(smsg);
            }
        /******************************************************************/
        }
    }

//    protected void getData(SystemMessage msg, int type, ObjectOutputStream obOut) throws Exception {
//        if (type == TOM_REQUEST_MSG || type == TOM_REPLY_MSG) {
//            getBytes((TOMMessage) msg, obOut);
//        } else if (type == RR_MSG || type == RT_MSG) {
//            obOut.writeObject(msg);
//        } else {//if (type == PAXOS_MSG){
//            getBytes((PaxosMessage) msg, obOut);
//        }
//    }

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

//    //utility methods to convert PaxosMessages to bytes and vice-versa
//    private PaxosMessage deserialise(PaxosMessage msg, DataOutput dout) throws Exception {
//        dout.writeInt(msg.getNumber());
//        dout.writeInt(msg.getRound());
//        dout.writeInt(msg.getSender());
//        dout.writeInt(msg.getPaxosType());
//        msg.serialise(dout);
//    }

    /*
    private PaxosMessage getPaxosMsg(ObjectInputStream obIn) throws Exception {
    int number = obIn.readInt();
    int round = obIn.readInt();
    int from = obIn.readInt();
    int paxosT = obIn.readInt();
    Object value = obIn.readObject();
    Object proof = obIn.readObject();
    return new PaxosMessage(paxosT, number, round, from, value, proof);
    }
     */

    //utility methods to convert TOMMessage to bytes and vice-versa
    private void getBytes(TOMMessage msg, ObjectOutputStream obOut) throws Exception {
        obOut.writeInt(msg.getSender());
        obOut.writeInt(msg.getSequence());
        obOut.writeObject(msg.getContent());
    }

    public SystemMessage deserialise(SystemMessage.Type type, DataInput in, byte[] verificationresult) throws IOException, ClassNotFoundException {
        switch(type){
            case FORWARDED:
                return new ForwardedMessage(in);
            case SM_MSG:
                return new SMMessage(in);
            default:
                Logger.println("Received msg for unknown msg type");
                return null;
        }
    }

    /*
    private TOMMessage getTOMMsg(ObjectInputStream obIn) throws Exception {
    int sender = obIn.readInt();
    int sequence = obIn.readInt();
    Object content = obIn.readObject();
    return new TOMMessage(sender, sequence, content, TOM_REQUEST_MSG);
    }
     */
}
