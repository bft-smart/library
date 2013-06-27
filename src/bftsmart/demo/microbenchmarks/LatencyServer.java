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
package bftsmart.demo.microbenchmarks;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.strategy.StandardStateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.SingleExecutable;
import bftsmart.tom.util.Storage;

/**
 * Simple server that just acknowledge the reception of a request.
 */
public class LatencyServer implements SingleExecutable, Recoverable {
    
    private int interval;
    private int hashs;
    private int replySize;
    
    private int iterations = 0;
    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage weakLatency = null;
    private Storage strongLatency = null;
    private ServiceReplica replica;
    private ReplicaContext replicaContext;

    private StateManager stateManager;

    public LatencyServer(int id, int interval, int hashs, int replySize) {
    	replica = new ServiceReplica(id, this, this);

        this.interval = interval;
        this.hashs = hashs;
        this.replySize = replySize;

        totalLatency = new Storage(interval);
        consensusLatency = new Storage(interval);
        preConsLatency = new Storage(interval);
        posConsLatency = new Storage(interval);
        proposeLatency = new Storage(interval);
        weakLatency = new Storage(interval);
        strongLatency = new Storage(interval);
    }
    
    public void setReplicaContext(ReplicaContext replicaContext) {
    	this.replicaContext = replicaContext;
    }

    @Override
    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    public byte[] execute(byte[] command, MessageContext msgCtx) {        
        iterations++;
        if(msgCtx.getConsensusId() == -1) {
            return new byte[replySize];
        }
        
        totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);
        consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
        preConsLatency.store(msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime);
        posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);
        proposeLatency.store(msgCtx.getFirstInBatch().weakSentTime - msgCtx.getFirstInBatch().consensusStartTime);
        weakLatency.store(msgCtx.getFirstInBatch().strongSentTime - msgCtx.getFirstInBatch().weakSentTime);
        strongLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().strongSentTime);

        if(iterations % interval == 0) {
            System.out.println("--- Measurements after "+ iterations+" ops ("+interval+" samples) ---");
            System.out.println("Total latency = " + totalLatency.getAverage(false) / 1000 + " (+/- "+ (long)totalLatency.getDP(false) / 1000 +") us ");
            totalLatency.reset();
            System.out.println("Consensus latency = " + consensusLatency.getAverage(false) / 1000 + " (+/- "+ (long)consensusLatency.getDP(false) / 1000 +") us ");
            consensusLatency.reset();
            System.out.println("Pre-consensus latency = " + preConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)preConsLatency.getDP(false) / 1000 +") us ");
            preConsLatency.reset();
            System.out.println("Pos-consensus latency = " + posConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)posConsLatency.getDP(false) / 1000 +") us ");
            posConsLatency.reset();
            System.out.println("Propose latency = " + proposeLatency.getAverage(false) / 1000 + " (+/- "+ (long)proposeLatency.getDP(false) / 1000 +") us ");
            proposeLatency.reset();
            System.out.println("Weak latency = " + weakLatency.getAverage(false) / 1000 + " (+/- "+ (long)weakLatency.getDP(false) / 1000 +") us ");
            weakLatency.reset();
            System.out.println("Strong latency = " + strongLatency.getAverage(false) / 1000 + " (+/- "+ (long)strongLatency.getDP(false) / 1000 +") us ");
            strongLatency.reset();
        }

        return new byte[replySize];
    }

    public static void main(String[] args){
        if(args.length < 4) {
            System.out.println("Use: java ...LatencyServer <processId> <measurement interval> <processing hashs> <reply size>");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        int hashs = Integer.parseInt(args[2]);
        int replySize = Integer.parseInt(args[3]);

        new LatencyServer(processId,interval,hashs,replySize);
    }

    public byte[] getState() {
        return new byte[0];
    }

    public void setState(byte[] state) {
    }

    @Override
    public ApplicationState getState(int eid, boolean sendState) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int setState(ApplicationState state) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public StateManager getStateManager() {
    	if(stateManager == null)
    		stateManager = new StandardStateManager();
    	return stateManager;
    }
   
}
