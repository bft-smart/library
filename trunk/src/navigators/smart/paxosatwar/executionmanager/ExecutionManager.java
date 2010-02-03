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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.paxosatwar.Consensus;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;


/**
 * This classe manages consensus instances. Each execution is a consensus
 * instance. It can have several rounds if there were problems during consensus.
 *
 * @author Alysson
 */
public final class ExecutionManager {

    private Acceptor acceptor; // Acceptor role of the PaW algorithm
    private Proposer proposer; // Proposer role of the PaW algorithm
    private int me; // This process ID
    private int[] acceptors; // Process ID's of all replicas, including this one
    private int[] otherAcceptors; // Process ID's of all replicas, except this one

    private Map<Integer, Execution> executions = new TreeMap<Integer, Execution>(); // Executions
    private ReentrantLock executionsLock = new ReentrantLock(); //lock for executions table

    // Paxos messages that were out of context (that didn't belong to the execution that was/is is progress
    private Map<Integer, List<PaxosMessage>> outOfContext = new HashMap<Integer, List<PaxosMessage>>();
    // Proposes that were out of context (that belonged to future executions, and not the one running at the time)
    private Map<Integer, PaxosMessage> outOfContextProposes = new HashMap<Integer, PaxosMessage>();
    private ReentrantLock outOfContextLock = new ReentrantLock(); //lock for out of context

    private boolean stopped = false; // Is the execution manager stopped?
    // When the execution manager is stopped, incoming paxos messages are stored here
    private List<PaxosMessage> stoppedMsgs = new LinkedList<PaxosMessage>();
    private Round stoppedRound = null; // round at which the current execution was stoppped
    private ReentrantLock stoppedMsgsLock = new ReentrantLock(); //lock for stopped messages

    public final int quorumF; // f replicas
    public final int quorum2F; // f * 2 replicas
    public final int quorumStrong; // ((n + f) / 2) replicas
    public final int quorumFastDecide; // ((n + 3 * f) / 2) replicas

    private TOMLayer tomLayer; // TOM layer associated with this execution manager
    private long initialTimeout; // initial timeout for rounds
    private int paxosHighMark; // Paxos high mark for consensus instances

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
    public ExecutionManager(Acceptor acceptor, Proposer proposer,
            int[] acceptors, int f, int me, long initialTimeout) {
        this.acceptor = acceptor;
        this.proposer = proposer;
        this.acceptors = acceptors;
        this.me = me;
        this.initialTimeout = initialTimeout;
        this.quorumF = f;
        this.quorum2F = 2 * f;
        this.quorumStrong = (int) Math.ceil((acceptors.length + f) / 2);
        this.quorumFastDecide = (int) Math.ceil((acceptors.length + 3 * f) / 2);
    }

    /**
     * Sets the TOM layer associated with this execution manager
     * @param tom The TOM layer associated with this execution manager
     */
    public void setTOMLayer(TOMLayer tom) {
        this.tomLayer = tom;
        this.paxosHighMark = tom.getConf().getPaxosHighMark();
    }

    /**
     * Returns the TOM layer associated with this execution manager
     * @return The TOM layer associated with this execution manager
     */
    public TOMLayer getTOMLayer() {
        return tomLayer;
    }

    /**
     * Returns this process ID
     * @return This process ID
     */
    public int getProcessId() {
        return me;
    }

    /**
     * Returns the process ID's of all replicas, including this one
     * @return Array of the process ID's of all replicas, including this one
     */
    public int[] getAcceptors() {
        return acceptors;
    }

    /**
     * Returns the process ID's of all replicas, except this one
     * @return Array of the process ID's of all replicas, except this one
     */
    public int[] getOtherAcceptors() {
        if (otherAcceptors == null) {
            otherAcceptors = new int[acceptors.length - 1];
            int c = 0;
            for (int i = 0; i < acceptors.length; i++) {
                if (acceptors[i] != me) {
                    otherAcceptors[c++] = acceptors[i];
                }
            }
        }

        return otherAcceptors;
    }

    /**
     * Returns the acceptor role of the PaW algorithm
     * @return The acceptor role of the PaW algorithm
     */
    public Acceptor getAcceptor() {
        return acceptor;
    }

    /**
     * Stops this execution manager
     */
    public void stop() {
        Logger.println("(ExecutionManager.stoping) Stoping execution manager");
        stoppedMsgsLock.lock();
        this.stopped = true;
        if (tomLayer.getInExec() != -1) {
            stoppedRound = getExecution(tomLayer.getInExec()).getLastRound();
            stoppedRound.getTimeoutTask().cancel();
            Logger.println("(ExecutionManager.stop) Stoping round " + stoppedRound.getNumber() + " of consensus " + stoppedRound.getExecution().getId());
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
        if (stoppedRound != null) {
            acceptor.scheduleTimeout(stoppedRound);
            stoppedRound = null;
        }

        //process stopped messages
        for (int i = 0; i < stoppedMsgs.size(); i++) {
            acceptor.processMessage(stoppedMsgs.remove(i));
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
        int consId = msg.getNumber();
        int lastConsId = tomLayer.getLastExec();

        boolean canProcessTheMessage = false;
        
        if (
                
                /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */

                // Isto serve para re-direccionar as mensagens para o out of context
                // enquanto a replica esta a receber o estado das outras e a actualizar-se

                tomLayer.isRetrievingState() ||
                    
                /******************************************************************/

                (consId > lastConsId  && (consId < (lastConsId + paxosHighMark)))

            ) { // Is this message within the low and high marks (or maybe is the replica synchronizing) ?

            if(stopped) {//just an optimization to avoid calling the lock in normal case
                stoppedMsgsLock.lock();
                if (stopped) {
                    Logger.println("(ExecutionManager.checkLimits) adding message for execution "+consId+" to stoopped");
                    //the execution manager was stopped, the messages should be stored
                    //for later processing (when the execution is restarted)
                    stoppedMsgs.add(msg);
                }
                stoppedMsgsLock.unlock();
            }

            if (
                    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */

                    // Isto serve para re-direccionar as mensagens para o out of context
                    // enquanto a replica esta a receber o estado das outras e a actualizar-se

                    tomLayer.isRetrievingState() ||

                    /******************************************************************/

                    consId > (lastConsId + 1)
            ) {
                Logger.println("(ExecutionManager.checkLimits) adding message for execution "+consId+" to out of context");
                //store it as an ahead of time message (out of context)
                addOutOfContextMessage(msg);
            } else {
                Logger.println("(ExecutionManager.checkLimits) message for execution "+consId+" can be processed");
                canProcessTheMessage = true;
            }
        } else if (consId >= (lastConsId + paxosHighMark)) { // Does this message exceeds the high mark?

            /**
            System.out.println("##################################################################################");
            System.out.println("- Ahead-of-time message (" + msg + ") discarded");
            System.out.println("- If many messages of the same consensus are discarded, the replica can halt!");
            System.out.println("- Try to increase the 'system.paxos.highMarc' configuration parameter.");
            System.out.println("- Last consensus executed: " + lastConsId);
            System.out.println("##################################################################################");
             /*/
            //TODO: at this point a new state should be recovered from other correct replicas

            /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            tomLayer.requestState(me, otherAcceptors, msg.getSender(), consId);
            /******************************************************************/
        }
        outOfContextLock.unlock();

        //br.ufsc.das.util.Logger.println("(checkLimits) Mensagem recebida nao estah dentro dos limites");    
        return canProcessTheMessage;
    }

    /**
     * Informs if there are messages till to be processed associated the specified consensus's execution
     * @param eid The ID for the consensus execution in question
     * @return True if there are still messages to be processed, false otherwise
     */
    public boolean thereArePendentMessages(int eid) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

        boolean result = outOfContextProposes.get(eid) != null || outOfContext.get(eid) != null;

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
    public void removeExecutions(int id) {
        executionsLock.lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/

        Set<Integer> keys = executions.keySet();
        for (int execution : keys)
                if (execution <= id) executions.remove(execution);

        /******* END EXECUTIONS CRITICAL SECTION *******/
        executionsLock.unlock();

        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

        keys = outOfContextProposes.keySet();
        for (int execution : keys)
                if (execution <= id) outOfContextProposes.remove(execution);

        keys = outOfContext.keySet();
        for (int execution : keys)
                if (execution <= id) outOfContext.remove(execution);

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }
    /********************************************************/

    /**
     * Returns the specified consensus's execution
     *
     * @param eid ID of the consensus's execution to be returned
     * @return The consensus's execution specified
     */
    public Execution getExecution(int eid) {
        executionsLock.lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/

        Execution execution = executions.get(eid);

        if (execution == null) {
            //there is no execution with the given eid

            //let's create one...
            execution = new Execution(this, new Consensus(proposer, eid, System.currentTimeMillis()),
                    initialTimeout);
            //...and add it to the executions table
            executions.put(eid, execution);

            /******* END EXECUTIONS CRITICAL SECTION *******/
            executionsLock.unlock();

            //now it is time to see if there are pending requests for this new
            //execution. First the propose...
            outOfContextLock.lock();
            /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

            PaxosMessage prop = outOfContextProposes.remove(eid);
            if (prop != null) {
                Logger.println("(ExecutionManager.createExecution) (" + eid + ") A processar PROPOSE recebido previamente fora de contexto");
                acceptor.processMessage(prop);
            }

            //then we have to put the pending paxos messages
            List<PaxosMessage> messages = outOfContext.remove(eid);
            if (messages != null) {
                Logger.println("(createExecution) (" + eid + ") A processar " + messages.size() + " mensagens recebidas previamente fora de contexto");
                for (Iterator<PaxosMessage> i = messages.iterator(); i.hasNext();) {
                    acceptor.processMessage(i.next());
                    if (execution.isDecided()) {
                        Logger.println("(ExecutionManager.createExecution) execution " + eid + " decided.");
                        break;
                    }
                }
                Logger.println("(createExecution) (" + eid + ") Terminei processamento de mensagens recebidas previamente fora de contexto");
            }

            /******* END OUTOFCONTEXT CRITICAL SECTION *******/
            outOfContextLock.unlock();
        } else {
            /******* END EXECUTIONS CRITICAL SECTION *******/
            executionsLock.unlock();
        }
        

        return execution;
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

    public String toString() {
        return stoppedMsgs.toString();
    }
}
