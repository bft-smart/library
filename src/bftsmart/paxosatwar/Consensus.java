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
package bftsmart.paxosatwar;

import bftsmart.paxosatwar.executionmanager.Round;
import bftsmart.tom.core.messages.TOMMessage;

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
