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

package navigators.smart.tom.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.util.DebugInfo;

/**
 *
 * @author Joao Sousa
 */
public class RandomServer extends ServiceReplica {

    private int value = 0;
    private int iterations = 0;

    public RandomServer(int id) {
        super(id);
    }


    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info) {
        iterations++;
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(command));
            int argument = input.readInt();
            int operator = input.readInt();

            System.out.println("[server] Argument: " + argument);
            switch (operator) {
                case 0:
                    value = value + argument;
                    System.out.println("[server] Operator: +");
                    break;
                case 1:
                    value = value - argument;
                    System.out.println("[server] Operator: -");
                    break;
                case 2:
                    value = value * argument;
                    System.out.println("[server] Operator: *");
                    break;
                case 3:
                    value = value / argument;
                    System.out.println("[server] Operator: /");
                    break;
            }
            //value += increment;
            if (info == null) System.out.println("[server] (" + iterations + ") Current value: " + value);
            else System.out.println("[server] (" + iterations + " / " + info.eid + ") Current value: " + value);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(value);
            return out.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(CounterServer.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java RandomServer <processId>");
            System.exit(-1);
        }

        new RandomServer(Integer.parseInt(args[0]));
    }

    @Override
    protected byte[] serializeState() {

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;

        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void deserializeState(byte[] state) {

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state[i] & 0x000000FF) << shift;
        }

        this.value = value;
    }

}
