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

package bftsmart.paxosatwar.messages;

/**
 * This class work as a factory of messages used in the paxos protocol.
 */
public class MessageFactory{

    // constants for messages types
    public static final int PROPOSE = 44781;
    public static final int WEAK    = 44782;
    public static final int STRONG  = 44783;
    public static final int DECIDE  = 44784;
    public static final int FREEZE  = 44785;
    public static final int COLLECT = 44786;

    private int from; // Replica ID of the process which sent this message

    /**
     * Creates a message factory
     * @param from Replica ID of the process which sent this message
     */
    public MessageFactory(int from) {

        this.from = from;

    }

    /**
     * Creates a PROPOSE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Proposed value
     * @param proof Proofs from other replicas
     * @return A paxos message of the PROPOSE type, with the specified id, round, value, and proof
     */
    public PaxosMessage createPropose(int id, int round, byte[] value,
            Object proof) {

        return new PaxosMessage(PROPOSE,id,round, from, value, proof);

    }

    /**
     * Creates a WEAK message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Weakly accepted value
     * @return A paxos message of the WEAK type, with the specified id, round, and value
     */
    public PaxosMessage createWeak(int id, int round, byte[] value) {

        return new PaxosMessage(WEAK,id,round, from, value);

    }

    /**
     * Creates a STRONG message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Strongly accepted value
     * @return A paxos message of the STRONG type, with the specified id, round, and value
     */
    public PaxosMessage createStrong(int id, int round, byte[] value) {

        return new PaxosMessage(STRONG,id,round, from, value);

    }

    /**
     * Creates a DECIDE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Decided value
     * @return A paxos message of the DECIDE type, with the specified id, round, and value
     */
    public PaxosMessage createDecide(int id, int round, byte[] value) {

         return new PaxosMessage(DECIDE,id,round, from, value);

    }

    /**
     * Creates a FREEZE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @return A paxos message of the FREEZE type, with the specified id, and round
     */
    public PaxosMessage createFreeze(int id, int round) {

        return new PaxosMessage(FREEZE,id,round, from);

    }

    /**
     * Creates a COLLECT message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param proof The proof to be sent by the leader for all replicas
     * @return A paxos message of the COLLECT type, with the specified id, round, and proof
     */
    public PaxosMessage createCollect(int id, int round, Object proof) {

        return new PaxosMessage(COLLECT,id,round, from, null, proof);

    }

}

