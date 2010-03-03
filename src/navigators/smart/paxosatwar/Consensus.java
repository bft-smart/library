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

package navigators.smart.paxosatwar;

import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.util.Logger;

/**
 * This class stands for an instance of a consensus
 */
public class Consensus {

    // TODO: Faz sentido existir aqui um proposer? Porque não ter isto antes no TOM layer?
    private Proposer proposer; // the proposer role of the PaW algorithm
    private int eid; // execution ID
    private byte[] proposal = null; // proposed value
    private byte[] decision = null; // decided value
    private Object deserializedDecision = null; // decided value (deserialized)
    private Round decisionRound = null;

    // TODO: Faz sentido ser public?
    public long startTime; // the consensus start time
    public long executionTime; // consensus execution time
    public int batchSize=0; //number of messages included in the batch

    /**
     * Creates a new instance of Consensus
     * @param proposer The proposer role of PaW algorithm
     * @param eid The execution ID for this consensus
     * @param startTime The consensus start time
     */
    public Consensus(Proposer proposer, int eid, long startTime) {
        this.proposer = proposer;
        this.eid = eid;
        this.startTime = startTime;
    }

    /**
     * The Execution ID for this consensus
     * @return Execution ID for this consensus
     */
    public int getId() {
        return eid;
    }

    /**
     * The proposer role of PaW algorithm
     * @return Proposer role of PaW algorithm
     */
    public byte[] getProposal() {
        return proposal;
    }

    /**
     * Sets the decided value
     * @return Decided Value
     */
    public byte[] getDecision() {

        if(decision == null) {
            waitForPropose();
            decision = decisionRound.propValue;
        }

        return decision;
    }

    /**
     * The deserialized decision
     * @return Deserialized decision
     */
    public Object getDeserializedDecision() {

        if(deserializedDecision == null) {
            waitForPropose();
            deserializedDecision = decisionRound.deserializedPropValue;
        }
        return deserializedDecision;
    }

    /**
     * The decision round (which contain all information about the decision)
     * @return the round in which this consensus was decided
     */
    public Round getDecisionRound() {
        return decisionRound;
    }

    /**
     * Sets the proposed value
     * @param value Proposed value
     */
    public void propose(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "A proposed value cannot be null!");
        }
        proposal = value;
        proposer.startExecution(eid, value);
    }

    public void decided(Round round) {
        decisionRound = round;
    }

    private void waitForPropose() {
        while(decisionRound.deserializedPropValue == null) {
            try{
                Logger.println("waiting for propose for "+eid);
                Thread.sleep(1);
            }catch(InterruptedException ie) {}
        }
    }
}