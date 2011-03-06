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


public class ThroughputTestServer extends ServiceReplica {
    
    private ServerCommunicationSystem cs;
    private int id;
    private int interval;
    private long numDecides=0;
    private long lastDecideTimeInstant;
    private long max=0;
    
    public ThroughputTestServer(int id, int interval) throws IOException {
        super(id);
        this.id = id;
        this.interval = interval;
    }

    public void receiveOrderedMessage(TOMMessage msg){

        long receiveInstant =  System.currentTimeMillis();          

        numDecides++;

        if (numDecides == 1) {

            lastDecideTimeInstant = receiveInstant;

        } else if (numDecides==interval) {

            long elapsedTime = receiveInstant - lastDecideTimeInstant;

            double opsPerSec_ = interval/(elapsedTime/1000.0);

            long opsPerSec = Math.round(opsPerSec_);

            if (opsPerSec>max)

                max = opsPerSec;

            System.out.println("Last "+interval+" decisions were done at a rate of " + opsPerSec + " ops per second");

            System.out.println("Maximum throughput until now: " + max + " ops per second");

            numDecides = -1;

        }

    }

    

    public static void main(String[] args){

        if(args.length < 2) {

            System.out.println("Use: java ThroughputTestServer <processId> <measurement interval (in messages)>");

            System.exit(-1);

        }
        try {
        new ThroughputTestServer(Integer.parseInt(args[0]),Integer.parseInt(args[1])).run();
        } catch (IOException ex) {
            Logger.getLogger(ThroughputTestServer.class.getName()).log(Level.SEVERE, null, ex);
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

