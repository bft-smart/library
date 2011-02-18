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

package navigators.smart.paxosatwar.executionmanager;

import navigators.smart.paxosatwar.roles.Acceptor;


/**
 * This class implements a timeout for consensus's rounds
 */
public class TimeoutTask implements Runnable {

    private Acceptor acceptor; // The acceptor role of the PaW algorithm
    private Round round; // The round to which this timeout is related to

    /**
     * Creates a new instance of TimeoutTask
     * @param acceptor The acceptor role of the PaW algorithm
     * @param round The round to which this timeout is related to
     */
    public TimeoutTask(Acceptor acceptor, Round round) {

        this.acceptor = acceptor;
        this.round = round;
    }

    /**
     * Runs this timer
     */
    public void run() {
        acceptor.timeout(round);
    }
}

