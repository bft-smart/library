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

package navigators.smart.tom.util;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.tom.core.TOMLayer;

/**
 * Print information about the replica when it is shutdown.
 *
 */
public class ShutdownThread extends Thread {

    private ServerCommunicationSystem scs;
    private LeaderModule lm;
    private Acceptor acceptor;
    private ExecutionManager manager;
    private TOMLayer tomLayer;

    public ShutdownThread(ServerCommunicationSystem scs, LeaderModule lm,
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
        System.err.println("Last executed leader: " +
                lm.getLeader(r.getExecution().getId(),r.getNumber()));
        System.err.println("State of the last executed round: "+r.toString());
        System.err.println("Consensus in execution: " + tomLayer.getInExec());
        if(tomLayer.getInExec() != -1) {
            Round r2 = manager.getExecution(tomLayer.getInExec()).getLastRound();
            if(r2 != null) {
                System.err.println("State of the round in execution: "+r2.toString());
            }
        }
        System.err.println("Execution manager: "+ tomLayer.execManager);
        System.err.println("Server communication system queues: "+
                scs.toString());
        //System.err.println("Pending requests: " +
        //        tomLayer.clientsManager.getPendingRequests());
        System.err.println("Requests timers: " + tomLayer.requestsTimer);
        System.err.println("---------- ---------- ----------");
    }
}
