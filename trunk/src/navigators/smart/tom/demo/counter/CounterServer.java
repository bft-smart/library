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

package navigators.smart.tom.demo.counter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;
import navigators.smart.statemanagment.ApplicationState;
import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.server.BatchExecutable;
import navigators.smart.tom.server.SingleExecutable;
import navigators.smart.tom.server.Recoverable;


/**
 * Example replica that implements a BFT replicated service (a counter).
 *
 */
public final class CounterServer implements BatchExecutable, Recoverable  {
    
    private ServiceReplica replica;
    private int counter = 0;
    private int iterations = 0;
    
    private MessageDigest md;
    private ReentrantLock stateLock = new ReentrantLock();
    private int lastEid = -1;
    
    public CounterServer(int id) {
    	replica = new ServiceReplica(id, this, this);

        try {
            md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
    }
    
     //******* EDUARDO BEGIN **************//
    public CounterServer(int id, boolean join) {
    	replica = new ServiceReplica(id, join, this, this);
    }
     //******* EDUARDO END **************//
    
    
    @Override
    public byte[][] executeBatch(byte[][] commands, MessageContext[] msgCtxs) {
        
        stateLock.lock();
        
        byte [][] replies = new byte[commands.length][];
        for (int i = 0; i < commands.length; i++) {
            replies[i] = execute(commands[i],msgCtxs[i]);
        }
        
        stateLock.unlock();
        
        return replies;
    }
    
    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
                
        return execute(command,msgCtx);
    }
    
    public byte[] execute(byte[] command, MessageContext msgCtx) {
        iterations++;
        try {
            int increment = new DataInputStream(new ByteArrayInputStream(command)).readInt();
            //System.out.println("read-only request: "+(msgCtx.getConsensusId() == -1));
            counter += increment;
            lastEid = msgCtx.getConsensusId();
            
            if (msgCtx.getConsensusId() == -1)
                System.out.println("(" + iterations + ") Counter incremented: " + counter);
            else
                System.out.println("(" + iterations + " / " + msgCtx.getConsensusId() + ") Counter incremented: " + counter);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(counter);
            return out.toByteArray();
        } catch (IOException ex) {
            System.err.println("Invalid request received!");
            return new byte[0];
        }
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java CounterServer <processId> <join option (optional)>");
            System.exit(-1);
        }

        if(args.length > 1) {
            new CounterServer(Integer.parseInt(args[0]), Boolean.valueOf(args[1]));
        }else{        
            new CounterServer(Integer.parseInt(args[0]));
        }
    }

    /** THIS IS JOAO'S CODE, TO HANDLE CHECKPOINTS */


    @Override
    public ApplicationState getState(int eid, boolean sendState) {
        
        stateLock.lock();
        
        if (eid == -1 || eid > lastEid) return new CounterState();
        
        byte[] b = new byte[4];
        byte[] d = null;

        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((counter >>> offset) & 0xFF);
        }

        stateLock.unlock();
        
        d = md.digest(b);
        
        return new CounterState(lastEid, (sendState ? b : null), d);
    }

    @Override
    public int setState(ApplicationState state) {

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state.getSerializedState()[i] & 0x000000FF) << shift;
        }

        //System.out.println("setting counter to: "+value);
        
        stateLock.lock();
        this.counter = value;
        stateLock.unlock();
        this.lastEid = state.getLastEid();
        return state.getLastEid();
    }

    /********************************************************/
}
