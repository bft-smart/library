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
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Logger;

/**
 *
 * This class represents a Consensus Instance.
 *
 * @param <E> Type of the decided Object
 *
 * @author unkown
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class Consensus {

    private int eid; // execution ID
    private Round decisionRound = null;
    private byte[] decision = null; // decided value
    private TOMMessage[] deserializedDecision = null; // decided value (deserialized)
    //private final Object sync = new Object();
    // TODO: Faz sentido ser public?
    public long startTime; // the consensus start time
    public long executionTime; // consensus execution time
    public int batchSize = 0; //number of messages included in the batch

    /**
     * Creates a new instance of Consensus
     * @param proposer The proposer role of PaW algorithm
     * @param eid The execution ID for this consensus
     * @param startTime The consensus start time
     */
    public Consensus(int eid, long startTime) {
        this.eid = eid;
        this.startTime = startTime;
    }

    public void decided(Round round) {
        //synchronized (sync) {
            //this.decision = decision;
            this.decisionRound = round;
            //sync.notifyAll();
        //}
    }

    public Round getDecisionRound() {
        return decisionRound;
    }

    /**
     * Sets the decided value
     * @return Decided Value
     */
    public byte[] getDecision() {
        //synchronized (sync) {  //TODO is this sync needed? cspann
            while (decision == null) {
                waitForPropose(); // Eduardo: deve ter um waitForDecision separando (agora funciona pq é só um sleep)
                decision = decisionRound.propValue;
            }
            return decision;
        //}
    }

    public TOMMessage[] getDeserializedDecision() {
        //synchronized (sync) {
            while (deserializedDecision == null) {
                waitForPropose();
                deserializedDecision = decisionRound.deserializedPropValue;
            }
        //}
        return deserializedDecision;
    }

    /**public void setDeserialisedDecision(TOMMessage[] deserialised) {

        //System.out.println("Chamou o setDeserialisedDecision......");
        //synchronized (sync) {
        if(this.deserializedDecision == null){
            this.deserializedDecision = deserialised;
        }
            //sync.notifyAll();
        //}

    }*/

    /**
     * The Execution ID for this consensus
     * @return Execution ID for this consensus
     */
    public int getId() {
        return eid;
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
