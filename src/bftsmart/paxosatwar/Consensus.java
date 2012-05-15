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
package bftsmart.paxosatwar;

import bftsmart.paxosatwar.executionmanager.Round;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Logger;

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
    
    //for benchmarking
    public TOMMessage firstMessageProposed = null;
    public int batchSize = 0;

    /**
     * Creates a new instance of Consensus
     * @param proposer The proposer role of PaW algorithm
     * @param eid The execution ID for this consensus
     * @param startTime The consensus start time
     */
    public Consensus(int eid) {
        this.eid = eid;
    }

    public void decided(Round round) {
        this.decisionRound = round;
    }

    public Round getDecisionRound() {
        return decisionRound;
    }

    /**
     * Sets the decided value
     * @return Decided Value
     */
    public byte[] getDecision() {
        while (decision == null) {
            waitForPropose(); // Eduardo: should have a separate waitForDecision  (works for now, because it is just a sleep)
            decision = decisionRound.propValue;
        }
        return decision;
    }

    public TOMMessage[] getDeserializedDecision() {
        while (deserializedDecision == null) {
            waitForPropose();
            deserializedDecision = decisionRound.deserializedPropValue;
        }
        return deserializedDecision;
    }

    /**
     * The Execution ID for this consensus
     * @return Execution ID for this consensus
     */
    public int getId() {
        return eid;
    }

    private void waitForPropose() {
        while (decisionRound == null &&
                decisionRound.deserializedPropValue == null) {
            try {
                System.out.println("waiting for propose for " + eid);
                Thread.sleep(1);
            } catch (InterruptedException ie) {
            }
        }
    }
}
