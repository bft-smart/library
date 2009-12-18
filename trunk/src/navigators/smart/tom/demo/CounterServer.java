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

package navigators.smart.tom.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.ServiceReplica;


/**
 * Example replica that implements a BFT replicated service (a counter).
 *
 */
public class CounterServer extends ServiceReplica {
    
    private int counter = 0;
    
    public CounterServer(int id) {
        super(id);
    }
    
    
    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command) {
        try {
            int increment = new DataInputStream(new ByteArrayInputStream(command)).readInt();
            counter += increment;
            System.out.println("[server] Counter incremented: " + counter);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(counter);
            return out.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(CounterServer.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java CounterServer <processId>");
            System.exit(-1);
        }

        new CounterServer(Integer.parseInt(args[0]));
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    @Override
    protected byte[] serializeState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void deserializeState(byte[] state) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    /********************************************************/
}
