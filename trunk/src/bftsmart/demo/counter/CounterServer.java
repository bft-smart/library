/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.counter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Recoverable;

/**
 * Example replica that implements a BFT replicated service (a counter).
 *
 */

public final class CounterServer implements BatchExecutable, Recoverable  {
    
    private int counter = 0;
    private int iterations = 0;
    private ReplicaContext replicaContext = null;
    
    private MessageDigest md;
    private ReentrantLock stateLock = new ReentrantLock();
    private int lastEid = -1;
    
    private StateManager stateManager;

    public CounterServer(int id) {
    	new ServiceReplica(id, this, this);

        try {
            md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
    }
    
     //******* EDUARDO BEGIN **************//
    public CounterServer(int id, boolean join) {
    	new ServiceReplica(id, join, this, this);
    }
     //******* EDUARDO END **************//
    
    public void setReplicaContext(ReplicaContext replicaContext) {
    	this.replicaContext = replicaContext;
    }
    
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

    @Override
    public StateManager getStateManager() {
    	if(stateManager == null)
    		stateManager = new StandardStateManager();
    	return stateManager;
    }

}
