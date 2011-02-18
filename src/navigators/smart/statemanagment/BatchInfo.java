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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.statemanagment;

import java.io.Serializable;
import java.util.Arrays;

/**
 *
 * @author Joao Sousa
 */
public class BatchInfo implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 7251994215265951672L;
	public final byte[] batch;
    public final int round;
    public final int leader;


    public BatchInfo () {
        this.batch = null;
        this.round = -1;
        this.leader = -1;
    }
    public BatchInfo(byte[] batch, int round, int leader) {
        this.batch = batch;
        this.round = round;
        this.leader = leader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BatchInfo) {
            BatchInfo bi = (BatchInfo) obj;
            return Arrays.equals(this.batch, bi.batch) && this.round == bi.round && this.leader == bi.leader;
        }
        return false;
    }

    @Override
    public int hashCode() {

        int hash = 1;

        if (this.batch != null) {
            for (int j = 0; j < this.batch.length; j++)
                hash = hash * 31 + this.batch[j];
        } else {
            hash = hash * 31 + 0;
        }

        hash = hash * 31 + this.round;
        hash = hash * 31 + this.leader;

        return hash;
    }
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "BatchInfo [batch=" + Arrays.toString(batch) + ", leader=" + leader + ", round=" + round + "]";
	}
}
