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

package navigators.smart.paxosatwar.roles;

import java.security.SignedObject;
import java.util.Timer;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.executionmanager.TimeoutTask;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.FreezeProof;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Proof;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.Logger;


/**
 * This class represents the acceptor role in the paxos protocol.
 * This class work together with the TOMulticastLayer class in order to
 * supply a atomic multicast service.
 *
 * @author Alysson Bessani
 */
public class Acceptor {

    private Timer timer = new Timer(); // scheduler for timeouts
    private int me; // This replica ID
    private ExecutionManager manager; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ProofVerifier verifier; // Verifier for proofs
    private ServerCommunicationSystem communication; // Replicas comunication system
    private LeaderModule leaderModule; // Manager for information about leaders
    private TOMLayer tomLayer; // TOM layer
    private AcceptedPropose nextProp = null; // next value to be proposed


    private ReconfigurationManager reconfManager;

    /**
     * Creates a new instance of Acceptor.
     * @param communication Replicas comunication system
     * @param factory Message factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Acceptor(ServerCommunicationSystem communication, MessageFactory factory,
            ProofVerifier verifier, LeaderModule lm, ReconfigurationManager manager) {
        this.communication = communication;
        this.communication.setAcceptor(this);
        this.me = manager.getStaticConf().getProcessId();
        this.factory = factory;
        this.verifier = verifier;
        this.leaderModule = lm;
        this.reconfManager = manager;
    }

    /**
     * Verifies the signature of a signed object
     * @param so Signed object to be verified
     * @param sender Replica id that supposably signed this object
     * @return True if the signature is valid, false otherwise
     */
    public boolean verifySignature(SignedObject so, int sender) {
        return this.verifier.validSignature(so, sender);
    }

    /**
     * Makes a RTCollect object with this process private key
     * @param rtc RTCollect object to be signed
     * @return A SignedObject containing 'rtc'
     */
    public SignedObject sign(RTCollect rtc) {
        return this.verifier.sign(rtc);
    }

    /**
     * Sets the execution manager for this acceptor
     * @param manager Execution manager for this acceptor
     */
    public void setManager(ExecutionManager manager) {
        this.manager = manager;
    }

    /**
     * Sets the TOM layer for this acceptor
     * @param tom TOM layer for this acceptor
     */
    public void setTOMLayer(TOMLayer tom) {
        this.tomLayer = tom;
    }

    /**
     * Called by communication layer to delivery paxos messages. This method
     * only verifies if the message can be executed and calls process message
     * (storing it on an out of context message buffer if this is not the case)
     *
     * @param msg Paxos messages delivered by the comunication layer
     */
    public final void deliver(PaxosMessage msg) {
        if (manager.checkLimits(msg)) {
            processMessage(msg);
        }else{
            if(msg.getPaxosType() == MessageFactory.PROPOSE){
                Logger.println("(Acceptor.deliver) Propose out of context: "+msg.getNumber());
            }
        }
    }

    /**
     * Called when a paxos message is received or when a out of context message must be processed.
     * It processes the received messsage acording to its type
     *
     * @param msg The message to be processed
     */
    public final void processMessage(PaxosMessage msg) {
        Execution execution = manager.getExecution(msg.getNumber());

        execution.lock.lock();

        Round round = execution.getRound(msg.getRound(), this.reconfManager);

        switch (msg.getPaxosType()) {
            case MessageFactory.PROPOSE:
                 {
                    proposeReceived(round, msg);
                }
                break;

            case MessageFactory.WEAK:
                 {
                    weakAcceptReceived(round, msg.getSender(), msg.getValue());
                }
                break;

            case MessageFactory.STRONG:
                 {
                    strongAcceptReceived(round, msg.getSender(), msg.getValue());
                }
                break;

            case MessageFactory.DECIDE:
                 {
                    decideReceived(round, msg.getSender(), msg.getValue());
                }
                break;

            case MessageFactory.FREEZE: {
                freezeReceived(round, msg.getSender());
            }
        }

        execution.lock.unlock();
    }

    /**
     * Called when a PROPOSE message is received or when processing a formerly out of context propose which
     * is know belongs to the current execution.
     *
     * @param msg The PROPOSE message to by processed
     */
    public void proposeReceived(Round round, PaxosMessage msg) {
        byte[] value = msg.getValue();
        int p = msg.getSender();
        int eid = round.getExecution().getId();

        Logger.println("(Acceptor.proposeReceived) PROPOSE for "+round.getNumber()+","+round.getExecution().getId()+" received from "+ p);

        /**********************************************************/
        /********************MALICIOUS CODE************************/
        /**********************************************************/
       /*
        if(leaderModule.getLeader(eid, msg.getRound()) == new Random().nextInt(conf.getN())) {
            try {
                Thread.sleep(conf.getFreezeInitialTimeout());
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Acceptor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        */
        /**********************************************************/
        /**********************************************************/
        /**********************************************************/

        // If message's round is 0, and the sender is the leader for the message's round,
        // execute the propose
        int test = leaderModule.getLeader(eid, msg.getRound());


        //System.out.println("eid "+eid);
        //System.out.println("p "+p);
        //System.out.println("test "+test);

        if (msg.getRound() == 0 && test == p) {
            executePropose(round, value);
        } else {
            Proof proof = (Proof) msg.getProof();
            if (proof != null) {
                // Get valid proofs
                CollectProof[] collected = verifier.checkValid(eid, msg.getRound() - 1, proof.getProofs());

                if (verifier.isTheLeader(p, collected)) { // Is the replica that sent this message the leader?
                    leaderModule.addLeaderInfo(eid, msg.getRound(), p);

                    // Is the proposed value good according to the PaW algorithm?
                    if (value != null && (verifier.good(value, collected, true))) {

                        executePropose(round, value);
                    } else if (checkAndDiscardConsensus(eid, collected, true)) {
                        leaderModule.addLeaderInfo(eid, 0, p);
                    }

                    //Is there a next value to be proposed, and is it good
                    //according to the PaW algorithm
                    if (proof.getNextPropose() != null && verifier.good(proof.getNextPropose(), collected, false)) {
                        int nextRoundNumber = verifier.getNextExecRound(collected);
                        if (tomLayer.getInExec() == eid + 1) { // Is this message from the previous execution?
                            Execution nextExecution = manager.getExecution(eid + 1);
                            nextExecution.removeRounds(nextRoundNumber - 1);

                            executePropose(nextExecution.getRound(nextRoundNumber, this.reconfManager), value);
                        } else {
                            nextProp = new AcceptedPropose(eid + 1, round.getNumber(), value, proof);
                        }
                    } else {
                        if (checkAndDiscardConsensus(eid + 1, collected, false)) {
                            leaderModule.addLeaderInfo(eid + 1, 0, p);
                        }
                    }
                }
            }
        }
    }

    /**
     * Discards information related to a consensus
     *
     * @param eid Consensus execution ID
     * @param proof
     * @param in
     * @return true if the leader have to be changed and false otherwise
     */
    private boolean checkAndDiscardConsensus(int eid, CollectProof[] proof, boolean in) {

        System.out.println("We entered in a weird place in the code :S");
        if (tomLayer.getLastExec() < eid) {
            if (verifier.getGoodValue(proof, in) == null) {
                //br.ufsc.das.util.//Logger.println("Descartando o consenso "+eid);
                if (tomLayer.getInExec() == eid) {
                    tomLayer.setInExec(-1);
                    System.out.println("I think we should process the out of context messages at this point");
                }
                Execution exec = manager.removeExecution(eid);
                if (exec != null) {
                    exec.removeRounds(-1);//cancela os timeouts dos rounds
                }
                if (tomLayer.getLastExec() + 1 == eid) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Called by the delivery thread. Executes the next accepted propose.
     *
     * @param eid Consensus's execution ID
     * @return True if there is a next value to be proposed and it belongs to
     * the specified execution, false otherwise
     */
    public boolean executeAcceptedPendent(int eid) {
        if (nextProp != null && nextProp.eid == eid) {
            Execution execution = manager.getExecution(eid);
            execution.lock.lock();

            Round round = execution.getRound(nextProp.r, this.reconfManager);
            executePropose(round, nextProp.value);
            nextProp = null;

            execution.lock.unlock();
            return true;
        } else {
            nextProp = null;
            return false;
        }
    }

    /**
     * Executes actions related to a proposed value.
     *
     * @param round the current round of the execution
     * @param value Value that is proposed
     */
    private void executePropose(Round round, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.executePropose) executing propose for " + eid + "," + round.getNumber());

        //System.out.println("Executou proposta para: "+eid);

        //System.out.println("(TESTE // Acceptor.executePropose) EID: " + eid + ", round: " + round.getNumber() + ", value: " + value.length);
        if(round.propValue == null) {
            round.propValue = value;
            round.propValueHash = tomLayer.computeHash(value);

            //start this execution if it is not already running
            if (eid == tomLayer.getLastExec() + 1) {
                tomLayer.setInExec(eid);
            }
            round.deserializedPropValue = tomLayer.checkProposedValue(value);

            //******* EDUARDO BEGIN **************//

            //Eduardo: tirei de dentro do if a linha abaixo, pois caso o processo
            //receba f+1 weaks e tb execute o seu weak antes de receber a proposta
            //nunca vai setar a decisao e a deliveryThread ficara bloqueada (deadlock).
            //se tiver problema caso for null, entao tem que colocar o teste antes (acho que nao tem problema)

            //round.getExecution().getLearner().setDeserialisedDecision(deserialised);

            if (round.deserializedPropValue != null && !round.isWeakSetted(me)) {
                //round.getExecution().getLearner().setDeserialisedDecision(deserialised);
                if(Logger.debug)
                    Logger.println("(Acceptor.executePropose) sending weak for " + eid);

                round.setWeak(me, round.propValueHash);
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        factory.createWeak(eid, round.getNumber(), round.propValueHash));

            //******* EDUARDO END **************//
                computeWeak(eid, round, round.propValueHash);
            }
        }
    }

    /**
     * Called when a WEAK message is received
     *
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void weakAcceptReceived(Round round, int a, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.weakAcceptReceived) WEAK from " + a + " for consensus " + eid);
        round.setWeak(a, value);

        if (!round.isWeakSetted(me)) {
            //******* EDUARDO BEGIN **************//
            if (round.countWeak(value) > reconfManager.getQuorumF()) {
            //******* EDUARDO END **************//
                if (eid == tomLayer.getLastExec() + 1) {
                    tomLayer.setInExec(eid);
                }

                Logger.println("(Acceptor.weakAcceptReceived) sending weak for " + eid);
                round.setWeak(me, value);
                //******* EDUARDO BEGIN **************//
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        factory.createWeak(eid, round.getNumber(), value));
                //******* EDUARDO END **************//
            }
        }

        computeWeak(eid, round, value);
    }

    /**
     * Computes weakly accepted values according to the standard PaW specification
     * (sends STRONG/DECIDE messages, according to the number of weakly accepted
     * values received).
     *
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeWeak(int eid, Round round, byte[] value) {
        int weakAccepted = round.countWeak(value);

        Logger.println("(Acceptor.computeWeak) I have " + weakAccepted +
                " weaks for " + eid + "," + round.getNumber());

        //******* EDUARDO BEGIN **************//
        if (weakAccepted > reconfManager.getQuorumStrong()) { // Can a send a STRONG message?
        //******* EDUARDO END **************//
            if (!round.isStrongSetted(me)) {
                Logger.println("(Acceptor.computeWeak) sending STRONG for " + eid);

                round.setStrong(me, value);
                //******* EDUARDO BEGIN **************//
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        factory.createStrong(eid, round.getNumber(), value));
                //******* EDUARDO END **************//

                computeStrong(eid, round, value);
            }

            //******* EDUARDO BEGIN **************//
            // Can I go straight to a DECIDE message?
            if (weakAccepted > reconfManager.getQuorumFastDecide() && !round.getExecution().isDecided()) {

                if (reconfManager.getStaticConf().isDecideMessagesEnabled()) {
                    round.setDecide(me, value);
                    communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                            factory.createDecide(eid, round.getNumber(), round.propValue));
                }
            //******* EDUARDO END **************//
                Logger.println("(Acceptor.computeWeak) Deciding " + eid);
                decide(round, value);
            }
        }
    }

    /**
     * Called when a STRONG message is received
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void strongAcceptReceived(Round round, int a, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.strongAcceptReceived) STRONG from " + a + " for consensus " + eid);
        round.setStrong(a, value);
        computeStrong(eid, round, value);
    }

    /**
     * Computes strongly accepted values according to the standard PaW specification (sends
     * DECIDE messages, according to the number of strongly accepted values received)
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeStrong(int eid, Round round, byte[] value) {
        Logger.println("(Acceptor.computeStrong) I have " + round.countStrong(value) +
                " strongs for " + eid + "," + round.getNumber());

        //******* EDUARDO BEGIN **************//
        if (round.countStrong(value) > reconfManager.getQuorum2F() && !round.getExecution().isDecided()) {

            if (reconfManager.getStaticConf().isDecideMessagesEnabled()) {
                round.setDecide(me, value);
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        factory.createDecide(eid, round.getNumber(), round.propValue));
            }
        //******* EDUARDO END **************//
            Logger.println("(Acceptor.computeStrong) Deciding " + eid);
            decide(round, value);
        }
    }

    /**
     * Called when a DECIDE message is received. Computes decided values
     * according to the standard PaW specification
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void decideReceived(Round round, int a, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.decideReceived) DECIDE from " + a + " for consensus " + eid);
        round.setDecide(a, value);

        //******* EDUARDO BEGIN **************//
        if (round.countDecide(value) > reconfManager.getQuorumF() && !round.getExecution().isDecided()) {
            if (reconfManager.getStaticConf().isDecideMessagesEnabled()) {
                round.setDecide(me, value);
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        factory.createDecide(eid, round.getNumber(), round.propValue));
            }
        //******* EDUARDO END **************//

            Logger.println("(Acceptor.decideReceived) Deciding " + eid);
            decide(round, value);
        } else if (round.getExecution().isDecided()) {
            Logger.println("(Acceptor.decideReceived) consensus " + eid + " already decided.");
        }
    }

    /**
     * Schedules a timeout for a given round. It is called by an Execution when a new round is created.
     * @param round Round to be associated with the timeout
     */
    /*public void scheduleTimeout(Round round) {
        Logger.println("(Acceptor.scheduleTimeout) (not) scheduling timeout of " + round.getTimeout() + " ms for round " + round.getNumber() + " of consensus " + round.getExecution().getId());
        TimeoutTask task = new TimeoutTask(this, round);
        round.setTimeoutTask(task);
        timer.schedule(task, round.getTimeout());
    }*/

    /**
     * This mehod is called by timertasks associated with rounds. It will locally freeze
     * a round, given that is not already frozen, its not decided, and is not removed from
     * its execution
     *
     * @param round
     */
    public void timeout(Round round) {
        Execution execution = round.getExecution();
        execution.lock.lock();

        Logger.println("(Acceptor.timeout) timeout for round " + round.getNumber() + " of consensus " + execution.getId());
        //System.out.println(round);

        if (!round.getExecution().isDecided() && !round.isFrozen() && !round.isRemoved()) {
            doFreeze(round);
            computeFreeze(round);
        }

        execution.lock.unlock();
    }

    /**
     * Called when a FREEZE message is received.
     * @param round Round of the receives message
     * @param a Replica that sent the message
     */
    private void freezeReceived(Round round, int a) {
        Logger.println("(Acceptor.freezeReceived) received freeze from " +a+ " for "+round.getNumber() + " of consensus " + round.getExecution().getId());
        round.addFreeze(a);
        //******* EDUARDO BEGIN **************//
        if (round.countFreeze() > reconfManager.getQuorumF() && !round.isFrozen()) {
        //******* EDUARDO END **************//
            doFreeze(round);
        }
        computeFreeze(round);
    }

    private void doFreeze(Round round) {
        Logger.println("(Acceptor.timeout) freezing round " + round.getNumber() + " of execution " + round.getExecution().getId());
        round.freeze();
        //******* EDUARDO BEGIN **************//
        communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                factory.createFreeze(round.getExecution().getId(), round.getNumber()));
        //******* EDUARDO END **************//
    }

    /**
     * Invoked when a timeout for a round is triggered, or when a FREEZE message is received.
     * Computes wether or not to locally freeze this round according to the standard PaW specification
     *
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeFreeze(Round round) {
        Logger.println("(Acceptor.computeFreeze) received " +round.countFreeze()+" freezes for round "+round.getNumber());
        //if there is more than 2f+1 timeouts
        if (round.countFreeze() > reconfManager.getQuorum2F() && !round.isCollected()) {
            round.collect();
            //round.getTimeoutTask().cancel();

            Execution exec = round.getExecution();
            Round nextRound = exec.getRound(round.getNumber() + 1,false, this.reconfManager);

            if (nextRound == null) { //If the next ro
                //create the next round
                nextRound = exec.getRound(round.getNumber() + 1, this.reconfManager);


                //define the leader for the next round: (previous_leader + 1) % N

                //******* EDUARDO BEGIN **************//
                 int pos = this.reconfManager.getCurrentViewPos(
                         leaderModule.getLeader(exec.getId(), round.getNumber()));

                 int newLeader =  this.reconfManager.getCurrentViewProcesses()[(pos + 1) % reconfManager.getCurrentViewN()];

                //int newLeader = (leaderModule.getLeader(exec.getId(), round.getNumber()) + 1)
                  //                                          % reconfManager.getStaticConf().getN();

                //******* EDUARDO END **************//

                leaderModule.addLeaderInfo(exec.getId(), nextRound.getNumber() + 1, newLeader);
                Logger.println("(Acceptor.computeFreeze) new leader for the next round of consensus is " + newLeader);

                if (exec.isDecided()) { //Does this process already decided a value?
                    //Even if I already decided, I should move to the next round to prevent
                    //process that not decided yet from blocking

                    Round decisionRound = exec.getDecisionRound();

                    //If the decision was reached on a previous round
                    if (round.getNumber() > decisionRound.getNumber()) {
                        Execution nextExec = manager.getExecution(exec.getId() + 1);
                        Round last = nextExec.getLastRound();
                        last.freeze();

                        CollectProof clProof = new CollectProof(createProof(exec.getId(), round),
                                createProof(exec.getId() + 1, last), newLeader);

                        communication.send(new int[]{newLeader},
                                factory.createCollect(exec.getId(), round.getNumber(), verifier.sign(clProof)));
                    }
                } else {
                    CollectProof clProof = new CollectProof(createProof(exec.getId(), round),
                            null, newLeader);
                    communication.send(new int[]{newLeader},
                            factory.createCollect(exec.getId(), round.getNumber(), verifier.sign(clProof)));
                }
            } else {
                Logger.println("(Acceptor.computeFreeze) I'm already executing round "+round.getNumber() + 1);
            }
        }
    }

    /**
     * Creates a freeze proof for the given execution ID and round
     *
     * @param eid Consensus's execution ID
     * @param r Round of the execution
     * @return A freez proof
     */
    private FreezeProof createProof(int eid, Round r) {
        return new FreezeProof(me, eid, r.getNumber(), r.getWeak(me),
                r.getStrong(me), r.getDecide(me));
    }

    /**
     * This is the metodh invoked when a value is decided by this process
     * @param round Round at which the decision is made
     * @param value The decided value (got from WEAK or STRONG messages)
     */
    private void decide(Round round, byte[] value) {
        leaderModule.decided(round.getExecution().getId(),
                leaderModule.getLeader(round.getExecution().getId(),
                round.getNumber()));

        //round.getTimeoutTask().cancel();
        round.getExecution().decided(round, value);
    }

    /**
     * This class is a data structure for a propose that was accepted
     */
    private class AcceptedPropose {

        public int eid;
        public int r;
        public byte[] value;
        public Proof p;

        public AcceptedPropose(int eid, int r, byte[] value, Proof p) {
            this.eid = eid;
            this.r = r;
            this.value = value;
            this.p = p;
        }
    }
}
