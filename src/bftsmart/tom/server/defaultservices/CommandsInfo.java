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
package bftsmart.tom.server.defaultservices;

import java.io.Serializable;
import java.util.Arrays;

import bftsmart.tom.MessageContext;

/**
 *
 * @author Joao Sousa
 */
public class CommandsInfo implements Serializable {
	
	private static final long serialVersionUID = 342711292879899682L;
	
	public final byte[][] commands;
	public final MessageContext[] msgCtx;
    public final int round;
    public final int leader;


    public CommandsInfo () {
        this.commands = null;
        this.msgCtx = null;
        this.round = -1;
        this.leader = -1;
    }
    
    public CommandsInfo(byte[][] commands, int round, int leader) {
    	this(commands, null, round, leader);
    }
    
    public CommandsInfo(byte[][] commands, MessageContext[] msgCtx, int round, int leader) {
        this.commands = commands;
        MessageContext[] onlyNeeded = null;
        if (msgCtx != null && msgCtx.length > 0) {
        	onlyNeeded = new MessageContext[msgCtx.length];
        	for(int i = 0; i < msgCtx.length; i++) {
				MessageContext msg = new MessageContext(
						msgCtx[i].getTimestamp(), null, msgCtx[i].getRegency(),
						msgCtx[i].getConsensusId(), msgCtx[i].getSender(),
						msgCtx[i].getFirstInBatch());
				onlyNeeded[i] = msg;
        	}
        }
        this.msgCtx = onlyNeeded;
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
