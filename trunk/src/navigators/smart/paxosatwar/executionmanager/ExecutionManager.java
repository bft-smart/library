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
package navigators.smart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.paxosatwar.Consensus;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.Logger;

/**
 * This classe manages consensus instances. Each execution is a consensus
 * instance. It can have several rounds if there were problems during consensus.
 *
 * @author Alysson
 */
public final class ExecutionManager {

    private ReconfigurationManager reconfManager;
    private Acceptor acceptor; // Acceptor role of the PaW algorithm
    private Proposer proposer; // Proposer role of the PaW algorithm
    //******* EDUARDO BEGIN: agora estas variaveis estao todas concentradas na Reconfigurationmanager **************//
    //private int me; // This process ID
    //private int[] acceptors; // Process ID's of all replicas, including this one
    //private int[] otherAcceptors; // Process ID's of all replicas, except this one
    //******* EDUARDO END **************//
    private Map<Integer, Execution> executions = new TreeMap<Integer, Execution>(); // Executions
    private ReentrantLock executionsLock = new ReentrantLock(); //lock for executions table
    // Paxos messages that were out of context (that didn't belong to the execution that was/is is progress
    private Map<Integer, List<PaxosMessage>> outOfContext = new HashMap<Integer, List<PaxosMessage>>();
    // Proposes that were out of context (that belonged to future executions, and not the one running at the time)
    private Map<Integer, PaxosMessage> outOfContextProposes = new HashMap<Integer, PaxosMessage>();
    private ReentrantLock outOfContextLock = new ReentrantLock(); //lock for out of context
    private boolean stopped = false; // Is the execution manager stopped?
    // When the execution manager is stopped, incoming paxos messages are stored here
    private Queue<PaxosMessage> stoppedMsgs = new LinkedList<PaxosMessage>();
    private ReentrantLock stoppedMsgsLock = new ReentrantLock(); //lock for stopped messages
    private TOMLayer tomLayer; // TOM layer associated with this execution manager
    private int paxosHighMark; // Paxos high mark for consensus instances
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    private int revivalHighMark; // Paxos high mark for consensus instances when this replica EID equals 0

    /******************************************************************/
    /**
     * Creates a new instance of ExecutionManager
     *
     * @param acceptor Acceptor role of the PaW algorithm
     * @param proposer Proposer role of the PaW algorithm
     * @param acceptors Process ID's of all replicas, including this one
     * @param f Maximum number of replicas that can be faulty
     * @param me This process ID
     * @param initialTimeout initial timeout for rounds
     */
    public ExecutionManager(ReconfigurationManager manager, Acceptor acceptor,
            Proposer proposer, int me) {
        //******* EDUARDO BEGIN **************//
        this.reconfManager = manager;
        this.acceptor = acceptor;
        this.proposer = proposer;
        //this.me = me;

        this.paxosHighMark = reconfManager.getStaticConf().getPaxosHighMark();
        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
        this.revivalHighMark = reconfManager.getStaticConf().getRevivalHighMark();
        /******************************************************************/
        //******* EDUARDO END **************//
    }

    /**
     * Sets the TOM layer associated with this execution manager
     * @param tom The TOM layer associated with this execution manager
     */
    public void setTOMLayer(TOMLayer tom) {
        this.tomLayer = tom;

    }

    /**
     * Returns the TOM layer associated with this execution manager
     * @return The TOM layer associated with this execution manager
     */
    public TOMLayer getTOMLayer() {
        return tomLayer;
    }

    /**
     * Returns the acceptor role of the PaW algorithm
     * @return The acceptor role of the PaW algorithm
     */
    public Acceptor getAcceptor() {
        return acceptor;
    }

    public Proposer getProposer() {
        return proposer;
    }

    /**
     * Stops this execution manager
     */
    public void stop() {
        Logger.println("(ExecutionManager.stoping) Stoping execution manager");
        stoppedMsgsLock.lock();
        this.stopped = true;
        int inExec = tomLayer.getInExec();
        if (inExec != -1) {

            Logger.println("(ExecutionManager.stop) Stoping consensus " + inExec);
        }
        stoppedMsgsLock.unlock();
    }

    /**
     * Restarts this execution manager
     */
    public void restart() {
        Logger.println("(ExecutionManager.restart) Starting execution manager");
        stoppedMsgsLock.lock();
        this.stopped = false;

        //process stopped messages
        while (!stoppedMsgs.isEmpty()) {
            acceptor.processMessage(stoppedMsgs.remove());
        }
        stoppedMsgsLock.unlock();
        Logger.println("(ExecutionManager.restart) Finished stopped messages processing");
    }

    /**
     * Checks if this message can execute now. If it is not possible,
     * it is stored in outOfContextMessages
     *
     * @param msg the received message
     * @return true in case the message can be executed, false otherwise
     */
    public final boolean checkLimits(PaxosMessage msg) {
        outOfContextLock.lock();
        
        int lastConsId = tomLayer.getLastExec();
        
        Logger.println("(ExecutionManager.checkLimits) Received message  " + msg);
        Logger.println("(ExecutionManager.checkLimits) I'm at execution " + 
                tomLayer.getInExec() + " and my last execution is " + lastConsId);
        
        boolean isRetrievingState = tomLayer.isRetrievingState();

        if (isRetrievingState) {
            Logger.println("(ExecutionManager.checkLimits) I'm waiting for a state");
        }

        boolean canProcessTheMessage = false;

        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
        // Isto serve para re-direccionar as mensagens para o out of context
        // enquanto a replica esta a receber o estado das outras e a actualizar-se
        if (isRetrievingState || // Is this replica retrieving a state?
                (!(lastConsId == -1 && msg.getNumber() >= (lastConsId + revivalHighMark)) && //not a recovered replica
                (msg.getNumber() > lastConsId && (msg.getNumber() < (lastConsId + paxosHighMark))))) { // not an ahead of time message

            if (stopped) {//just an optimization to avoid calling the lock in normal case
                stoppedMsgsLock.lock();
                if (stopped) {
                    Logger.println("(ExecutionManager.checkLimits) adding message for execution " + msg.getNumber() + " to stoopped");
                    //the execution manager was stopped, the messages should be stored
                    //for later processing (when the execution is restarted)
                    stoppedMsgs.add(msg);
                }
                stoppedMsgsLock.unlock();
            } else {
                if (isRetrievingState || 
                        msg.getNumber() > (lastConsId + 1) || 
                        (tomLayer.getInExec() == -1 && msg.getPaxosType() != MessageFactory.PROPOSE)) { //not propose message for the next consensus
                    Logger.println("(ExecutionManager.checkLimits) Message for execution " + 
                            msg.getNumber() + " is out of context, adding it to out of context set");
                    addOutOfContextMessage(msg);
                } else { //can process!
                    Logger.println("(ExecutionManager.checkLimits) message for execution " + 
                            msg.getNumber() + " can be processed");
                    
                    canProcessTheMessage = true;
                }
            }
        } else if ((lastConsId == -1 && msg.getNumber() >= (lastConsId + revivalHighMark)) || //recovered...
                (msg.getNumber() >= (lastConsId + paxosHighMark))) { //or too late replica

            //Start state transfer
            /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            Logger.println("(ExecutionManager.checkLimits) Message for execution "
                    + msg.getNumber() + " is beyond the paxos highmark, adding it to out of context set");
            addOutOfContextMessage(msg);

            if (reconfManager.getStaticConf().isStateTransferEnabled())
                tomLayer.getStateManager().requestState(msg.getSender(),  msg.getNumber());

            else {
                System.out.println("##################################################################################");
                System.out.println("- Ahead-of-time message discarded");
                System.out.println("- If many messages of the same consensus are discarded, the replica can halt!");
                System.out.println("- Try to increase the 'system.paxos.highMarc' configuration parameter.");
                System.out.println("- Last consensus executed: " + lastConsId);
                System.out.println("##################################################################################");
            }
            /******************************************************************/
        }
        
        outOfContextLock.unlock();

        return canProcessTheMessage;
    }

    
    
    /**
     * Informs if there are messages till to be processed associated the specified consensus's execution
     * @param eid The ID for the consensus execution in question
     * @return True if there are still messages to be processed, false otherwise
     */
    public boolean receivedOutOfContextPropose(int eid) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        boolean result = outOfContextProposes.get(eid) != null;
        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();

        return result;
    }

    /**
     * Removes a consensus's execution from this manager
     * @param id ID of the consensus's execution to be removed
     * @return The consensus's execution that was removed
     */
    public Execution removeExecution(int id) {
        executionsLock.lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/
        Execution execution = executions.remove(id);

        /******* END EXECUTIONS CRITICAL SECTION *******/
        executionsLock.unlock();

        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextProposes.remove(id);
        outOfContext.remove(id);

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();

        return execution;
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    public void removeOutOfContexts(int id) {

        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        Integer[] keys = new Integer[outOfContextProposes.keySet().size()];
        outOfContextProposes.keySet().toArray(keys);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] <= id) {
                outOfContextProposes.remove(keys[i]);
            }
        }

        keys = new Integer[outOfContext.keySet().size()];
        outOfContext.keySet().toArray(keys);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] <= id) {
                outOfContext.remove(keys[i]);
            }
        }

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }

    /********************************************************/
    /**
     * Returns the specified consensus' execution
     *
     * @param eid ID of the consensus execution to be returned
     * @return The consensus execution specified
     */
    public Execution getExecution(int eid) {
        executionsLock.lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/
        
        Execution execution = executions.get(eid);

        if (execution == null) {//there is no execution created with the given eid
            //let's create one...
            Consensus cons = new Consensus(eid);

            execution = new Execution(this, cons);

            //...and add it to the executions table
            executions.put(eid, execution);
        }

        /******* END EXECUTIONS CRITICAL SECTION *******/
        executionsLock.unlock();

        return execution;
    }

    public void processOutOfContextPropose(Execution execution) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        
        PaxosMessage prop = outOfContextProposes.remove(execution.getId());
        if (prop != null) {
            Logger.println("(ExecutionManager.createExecution) (" + execution.getId()
                    + ") Processing out of context propose");
            acceptor.processMessage(prop);
        }

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }

    public void processOutOfContext(Execution execution) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        
        //then we have to put the pending paxos messages
        List<PaxosMessage> messages = outOfContext.remove(execution.getId());
        if (messages != null) {
            Logger.println("(createExecution) (" + execution.getId()
                    + ") Processing other " + messages.size()
                    + " out of context messages.");

            for (Iterator<PaxosMessage> i = messages.iterator(); i.hasNext();) {
                acceptor.processMessage(i.next());
                if (execution.isDecided()) {
                    Logger.println("(ExecutionManager.createExecution) execution "
                            + execution.getId() + " decided.");
                    break;
                }
            }
            Logger.println("(createExecution) (" + execution.getId()
                    + ") Finished out of context processing");
        }

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }

    /**
     * Stores a message established as being out of context (a message that
     * doesn't belong to current executing consensus).
     *
     * @param m Out of context message to be stored
     */
    private void addOutOfContextMessage(PaxosMessage m) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        if (m.getPaxosType() == MessageFactory.PROPOSE) {
            outOfContextProposes.put(m.getNumber(), m);
        } else {
            List<PaxosMessage> messages = outOfContext.get(m.getNumber());
            if (messages == null) {
                messages = new LinkedList<PaxosMessage>();
                outOfContext.put(m.getNumber(), messages);
            }
            messages.add(m);

            if (outOfContext.size() % 1000 == 0) {
                Logger.println("(ExecutionManager.addOutOfContextMessage) out-of-context size: " + outOfContext.size());
            }
        }

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }

    @Override
    public String toString() {
        return stoppedMsgs.toString();
    }
}
