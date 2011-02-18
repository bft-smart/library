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
package navigators.smart.consensus;

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
public class Consensus<E> {

    private long eid; // execution ID
    private int decisionRound = 0;
    private byte[] decision = null; // decided value
    private E deserializedDecision = null; // decided value (deserialized)
    private final Object sync = new Object();

    public Consensus(long eid) {
        this.eid = eid;
    }

    public void decided(byte[] decision, int round) {
        synchronized (sync) {
            this.decision = decision;
            this.decisionRound = round;
            sync.notifyAll();
        }
    }

    public int getDecisionRound() {
        return decisionRound;
    }

    /**
     * Sets the decided value
     * @return Decided Value
     */
    public byte[] getDecision() {
        synchronized (sync) {  //TODO is this sync needed? cspann
            if (decision == null) {
                waitForPropose();
            }
            return decision;
        }
    }

    public void setDeserialisedDecision(E deserialised) {
        this.deserializedDecision = deserialised;
    }

    public E getDeserializedDecision() {
        synchronized (sync) {
            if (deserializedDecision == null) {
                waitForPropose();
            }
        }
        return deserializedDecision;
    }

    /**
     * The Execution ID for this consensus
     * @return Execution ID for this consensus
     */
    public long getId() {
        return eid;
    }

    private void waitForPropose() {
        synchronized (sync) {
            try {
                Logger.println("waiting for propose for " + eid);
                sync.wait();
            } catch (InterruptedException ex) {
                Logger.println(ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    @Override
    public String toString(){
        return "Consensus - EID: "+ eid +" Round: "+decisionRound;
    }
}
