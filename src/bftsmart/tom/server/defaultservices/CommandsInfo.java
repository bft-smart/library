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

package bftsmart.tom.server.defaultservices;

import java.io.Serializable;
import java.util.Arrays;

/**
 *
 * @author Joao Sousa
 */
public class CommandsInfo implements Serializable {
	
	private static final long serialVersionUID = 342711292879899682L;
	
	public final byte[][] commands;
    public final int round;
    public final int leader;


    public CommandsInfo () {
        this.commands = null;
        this.round = -1;
        this.leader = -1;
    }
    public CommandsInfo(byte[][] commands, int round, int leader) {
        this.commands = commands;
        this.round = round;
        this.leader = leader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CommandsInfo) {
            CommandsInfo ci = (CommandsInfo) obj;

            if ((this.commands != null && ci.commands == null) ||
                    (this.commands == null && ci.commands != null)) {
                //System.out.println("[CommandsInfo] returing FALSE!1");
                return false;
            }

            if (this.commands != null && ci.commands != null) {

                if (this.commands.length != ci.commands.length) {
                    //System.out.println("[CommandsInfo] returing FALSE!2");
                    return false;
                }
                
                for (int i = 0; i < this.commands.length; i++) {
                    
                    if (this.commands[i] == null && ci.commands[i] != null) {
                        //System.out.println("[CommandsInfo] returing FALSE!3");
                        return false;
                    }

                    if (this.commands[i] != null && ci.commands[i] == null) {
                        //System.out.println("[CommandsInfo] returing FALSE!4");
                        return false;
                    }
                    
                    if (!(this.commands[i] == null && ci.commands[i] == null) &&
                        (!Arrays.equals(this.commands[i], ci.commands[i]))) {
                        //System.out.println("[CommandsInfo] returing FALSE!5" + (this.commands[i] == null) + " " + (ci.commands[i] == null));
                        return false;
                    }
                }
            }
            //System.out.print("[CommandsInfo] returnig........");
            //System.out.println((this.round == ci.round) + " " + (this.leader == ci.leader));
            return this.round == ci.round && this.leader == ci.leader;
        }
        //System.out.println("[CommandsInfo] returing FALSE!");
        return false;
    }

    @Override
    public int hashCode() {

        int hash = 1;
        
        if (this.commands != null) {
            for (int i = 0; i < this.commands.length; i++) {
                if (this.commands[i] != null) {
                    for (int j = 0; j < this.commands[i].length; j++)
                        hash = hash * 31 + (int) this.commands[i][j];
                } else {
                    hash = hash * 31 + 0;
                }
            }
        } else {
            hash = hash * 31 + 0;
        }

        hash = hash * 31 + this.round;
        hash = hash * 31 + this.leader;

        return hash;
    }
}
