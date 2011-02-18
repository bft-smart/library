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


/**
 * This class stands for an instance of a consensus which measures the executiontime
 * @param <E> Type of the decidedupon values
 */
public class MeasuringConsensus<E> extends Consensus<E>{

    // TODO: Faz sentido existir aqui um proposer? Porque não ter isto antes no TOM layer?
    //private Proposer proposer; // the proposer role of the PaW algorithm

    // TODO: Faz sentido ser public?
    public long startTime; // the consensus start time
    public long executionTime; // consensus execution time
    public int batchSize=0; //number of messages included in the batch

    /**
     * Creates a new instance of MeasuringConsensus
     * @param eid The execution ID for this consensus
     * @param startTime The consensus start time
     */
    public MeasuringConsensus(long eid, long startTime) {
        super(eid);
        this.startTime = startTime;
    }
}