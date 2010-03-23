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

package navigators.smart.tom.demo;

import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


public class LatencyTestServerReplica0 extends TOMReceiver {

    private ServerCommunicationSystem cs;
    private int id;

    /** Creates a new instance of TOMServerPerformanceTest */
    public LatencyTestServerReplica0(int id) {
        this.id = id;
    }

    public void run(){
        //create the configuration object
        TOMConfiguration conf = new TOMConfiguration(id);
        try {
            //create the communication system
            cs = new ServerCommunicationSystem(conf);
        } catch (Exception ex) {
            Logger.getLogger(LatencyTestServer.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to build a communication system.");
        }
        //build the TOM server stack

        this.init(cs,conf);
        /**IST OE CODIGO DO JOAO, PARA TENTAR RESOLVER UM BUG */
        cs.start();
        /******************************************************/
    }

    public void receiveOrderedMessage(TOMMessage msg){
        TOMMessage reply = new TOMMessage(id,msg.getSequence(),
                msg.getContent());

//        //Logger.println("request received: "+msg.getSender()+
//                ","+msg.getSequence());

        cs.send(new int[]{msg.getSender()},reply);
    }

    public static void main(String[] args){
        /*
        if(args.length < 1) {
            System.out.println("Use: java LatencyTestServer <processId>");
            System.exit(-1);
        }

        new LatencyTestServer(Integer.parseInt(args[0])).run();
        */
        new LatencyTestServerReplica0(0).run();
    }
    
    public byte[] getState() {
        return null;
    }

    public void setState(byte[] state) {

    }

    public void receiveMessage(TOMMessage msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
