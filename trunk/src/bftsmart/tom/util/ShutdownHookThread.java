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
package bftsmart.tom.util;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.executionmanager.ExecutionManager;
import bftsmart.consensus.executionmanager.LeaderModule;
import bftsmart.consensus.Round;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.tom.core.TOMLayer;

/**
 * Print information about the replica when it is shutdown.
 *
 */
public class ShutdownHookThread extends Thread {

    private ServerCommunicationSystem scs;
    private LeaderModule lm;
    private Acceptor acceptor;
    private ExecutionManager manager;
    private TOMLayer tomLayer;

    public ShutdownHookThread(ServerCommunicationSystem scs, LeaderModule lm,
            Acceptor acceptor, ExecutionManager manager, TOMLayer tomLayer) {
        this.scs = scs;
        this.lm = lm;
        this.acceptor = acceptor;
        this.manager = manager;
        this.tomLayer = tomLayer;
    }

    @Override
    public void run() {
        System.err.println("---------- DEBUG INFO ----------");
        System.err.println("Current time: " + System.currentTimeMillis());
        System.err.println("Last executed consensus: " + tomLayer.getLastExec());
        Round r = manager.getExecution(tomLayer.getLastExec()).getLastRound();
        //******* EDUARDO BEGIN **************//
        if(r != null){
            System.err.println("Last executed leader: " + tomLayer.lm.getCurrentLeader()/*lm.getLeader(r.getExecution().getId(),r.getNumber())*/);
            System.err.println("State of the last executed round: "+r.toString());
        }
        //******* EDUARDO END **************//
        System.err.println("Consensus in execution: " + tomLayer.getInExec());
        if(tomLayer.getInExec() != -1) {
            Round r2 = manager.getExecution(tomLayer.getInExec()).getLastRound();
            if(r2 != null) {
                System.out.println("Consensus in execution leader: " + tomLayer.lm.getCurrentLeader()/*lm.getLeader(r2.getExecution().getId(),r.getNumber())*/);
                System.err.println("State of the round in execution: "+r2.toString());
            }
        }
        //System.err.println("Execution manager: "+ tomLayer.execManager);
        //System.err.println("Server communication system queues: "+scs.toString());
        //System.err.println("Pending requests: " +
        //        tomLayer.clientsManager.getPendingRequests());
        //System.err.println("Requests timers: " + tomLayer.requestsTimer);
        
        //System.out.println("Pending Requests: " + tomLayer.clientsManager.getPendingRequests());
        System.err.println("---------- ---------- ----------");
    }
}
