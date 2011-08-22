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

package navigators.smart.tom.demo.microbenchmarks;

import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.Storage;

/**
 * Simple server that just acknowledge the reception of a request.
 */
public class LatencyServer extends ServiceReplica {
    
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

    public LatencyServer(int id, int interval, int hashs, int replySize) {
        super(id);

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
    
    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, boolean readOnly, DebugInfo info) {        
        iterations++;
        if(info == null) {
            return new byte[replySize];
        }
        
        info.msg.executedTime = System.nanoTime();

        /*
        System.out.println("message received at "+info.msg.receptionTime/1000);
        System.out.println("consensus started at "+info.msg.consensusStartTime/1000);
        System.out.println("propose received at "+info.msg.proposeReceivedTime/1000);
        System.out.println("weak sent at "+info.msg.weakSentTime/1000);
        System.out.println("strong sent at "+info.msg.strongSentTime/1000);
        System.out.println("decided at "+info.msg.decisionTime/1000);
        System.out.println("delivered at "+info.msg.deliveryTime/1000);
        System.out.println("executed at "+info.msg.executedTime/1000);
      */
        
        totalLatency.store(info.msg.executedTime - info.msg.receptionTime);
        consensusLatency.store(info.msg.decisionTime - info.msg.consensusStartTime);
        preConsLatency.store(info.msg.consensusStartTime - info.msg.receptionTime);
        posConsLatency.store(info.msg.executedTime - info.msg.decisionTime);
        proposeLatency.store(info.msg.weakSentTime - info.msg.consensusStartTime);
        weakLatency.store(info.msg.strongSentTime - info.msg.weakSentTime);
        strongLatency.store(info.msg.decisionTime - info.msg.strongSentTime);

        if(iterations % interval == 0) {
            System.out.println("------------------------------------------------");
            System.out.println("Total latency (" + interval + " executions) = " + totalLatency.getAverage(false) / 1000 + " (+/- "+ (long)totalLatency.getDP(false) / 1000 +") us ");
            totalLatency.reset();
            System.out.println("Consensus latency (" + interval + " executions) = " + consensusLatency.getAverage(false) / 1000 + " (+/- "+ (long)consensusLatency.getDP(false) / 1000 +") us ");
            consensusLatency.reset();
            System.out.println("Pre-consensus latency (" + interval + " executions) = " + preConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)preConsLatency.getDP(false) / 1000 +") us ");
            preConsLatency.reset();
            System.out.println("Pos-consensus latency (" + interval + " executions) = " + posConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)posConsLatency.getDP(false) / 1000 +") us ");
            posConsLatency.reset();
            System.out.println("Propose latency (" + interval + " executions) = " + proposeLatency.getAverage(false) / 1000 + " (+/- "+ (long)proposeLatency.getDP(false) / 1000 +") us ");
            proposeLatency.reset();
            System.out.println("Weak latency (" + interval + " executions) = " + weakLatency.getAverage(false) / 1000 + " (+/- "+ (long)weakLatency.getDP(false) / 1000 +") us ");
            weakLatency.reset();
            System.out.println("Strong latency (" + interval + " executions) = " + strongLatency.getAverage(false) / 1000 + " (+/- "+ (long)strongLatency.getDP(false) / 1000 +") us ");
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

    @Override
    protected byte[] serializeState() {
        return new byte[0];
    }

    @Override
    protected void deserializeState(byte[] state) {
    }
}
