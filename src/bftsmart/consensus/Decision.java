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
package bftsmart.consensus;

import bftsmart.tom.core.messages.TOMMessage;

/**
 *
 * This class represents a Consensus Instance.
 *
 * @param <E> Type of the decided Object
 *
 * @author Joao Sousa
 * @author Alysson Bessani
 */
public class Decision {

    private final int eid; // execution ID
    private Epoch decisionEpoch = null;
    private byte[] value = null; // decided value
    private TOMMessage[] deserializedValue = null; // decided value (deserialized)
    
    //for benchmarking
    public TOMMessage firstMessageProposed = null;
    public int batchSize = 0;

    /**
     * Creates a new instance of Decision
     * @param eid The ID for the respective consensus
     */
    public Decision(int eid) {
        this.eid = eid;
    }

    /**
     * Set epoch in which the value was decided
     * @param epoch The epoch in which the value was decided
     */
    public void setDecisionEpoch(Epoch epoch) {
        this.decisionEpoch = epoch;
    }


    /**
     * Get epoch in which the value was decided
     * 
     * @return The epoch in which the value was decided
     */
    public Epoch getDecisionEpoch() {
        return decisionEpoch;
    }
    
    /**
     * Sets the decided value
     * @return Decided Value
     */
    public byte[] getValue() {
        while (value == null) {
            waitForPropose(); // Eduardo: should have a separate waitForDecision  (works for now, because it is just a sleep)
            value = decisionEpoch.propValue;
        }
        return value;
    }

    public TOMMessage[] getDeserializedValue() {
        while (deserializedValue == null) {
            waitForPropose();
            deserializedValue = decisionEpoch.deserializedPropValue;
        }
        return deserializedValue;
    }

    /**
     * The ID for the associated consensus
     * @return ID for the associated consensus
     */
    public int getConsensusId() {
        return eid;
    }

    private void waitForPropose() {
        while (decisionEpoch == null &&
                decisionEpoch.deserializedPropValue == null) {
            try {
                System.out.println("waiting for propose for consensus" + eid);
                Thread.sleep(1);
            } catch (InterruptedException ie) {
            }
        }
    }
}
