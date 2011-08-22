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

package navigators.smart.tom.util;

import navigators.smart.tom.core.messages.TOMMessage;

/**
 *
 * @author Joao Sousa
 */
public class DebugInfo {

    public final int eid;
    public final int round;
    public final int leader;
    public final TOMMessage msg;

    public DebugInfo(int eid, int round, int leader, TOMMessage msg) {
        this.eid = eid;
        this.round = round;
        this.leader = leader;
        this.msg = msg;
    }
}
