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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.TOMConfiguration;


public class LatencyTestServerReplica0 extends ServiceReplica {

    private ServerCommunicationSystem cs;
    private int id;

    /** Creates a new instance of TOMServerPerformanceTest */
    public LatencyTestServerReplica0(int id) throws IOException {
        super(id);
        this.id = id;
    }

    public void receiveOrderedMessage(TOMMessage msg){
        TOMMessage reply = new TOMMessage(id,msg.getSequence(),
                msg.getContent());

        cs.send(new int[]{msg.getSender()},reply);
    }

    public static void main(String[] args){
        try {
        /*
        if(args.length < 1) {
            System.out.println("Use: java LatencyTestServer <processId>");
            System.exit(-1);
        }
        new LatencyTestServer(Integer.parseInt(args[0])).run();
        */
        new LatencyTestServerReplica0(0).run();
        } catch (IOException ex) {
            Logger.getLogger(LatencyTestServerReplica0.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public byte[] getState() {
        return null;
    }

    public void setState(byte[] state) {

    }

    public void receiveUnorderedMessage(TOMMessage msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected byte[] serializeState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void deserializeState(byte[] state) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
