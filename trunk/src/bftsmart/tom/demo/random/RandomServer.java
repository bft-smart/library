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

package bftsmart.tom.demo.random;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

import bftsmart.statemanagment.ApplicationState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.Executable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;

/**
 *
 * @author Joao Sousa
 */
public final class RandomServer extends DefaultRecoverable {

    private int value = 0;
    private int iterations = 0;
    /**********ISTO E CODIGO MARTELADO, PARA FAZER AVALIACOES **************/
    private int id = -1;
    private long initialTime = -1;
    private long currentTime = -1;
    /***********************************************************************/
    private ServiceReplica replica;
    private ReplicaContext replicaContext;
    
    public ServiceReplica getReplica() {
		return replica;
	}

	public void setReplica(ServiceReplica replica) {
		this.replica = replica;
	}

    public void setReplicaContext(ReplicaContext replicaContext) {
    	this.replicaContext = replicaContext;
    }

    public RandomServer(int id) {
    	replica = new ServiceReplica(id, this, this);
        this.id = id;
    }

    public RandomServer(int id, boolean join) {
    	replica = new ServiceReplica(id, join, this, this);
        this.id = id;
    }

    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        iterations++;
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(command));
            int argument = input.readInt();
            int operator = input.readInt();

            System.out.println("(" + id + ")[server] Argument: " + argument);
            switch (operator) {
                case 0:
                    value = value + argument;
                    System.out.println("(" + id + ")[server] Operator: +");
                    break;
                case 1:
                    value = value - argument;
                    System.out.println("(" + id + ")[server] Operator: -");
                    break;
                case 2:
                    value = value * argument;
                    System.out.println("(" + id + ")[server] Operator: *");
                    break;
                case 3:
                    value = value / argument;
                    System.out.println("(" + id + ")[server] Operator: /");
                    break;
            }
            
            if (msgCtx != null) System.out.println("(" + id + ")[server] (" + iterations + " / " + 
                    msgCtx.getConsensusId() + " / " + msgCtx.getRegency() + ") Current value: " + value);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(value);
            return out.toByteArray();
        } catch (IOException ex) {
            System.err.println("Invalid request received!");
            return new byte[0];
        }
    }

    /**
     * Just return the current value of the counter.
     * 
     * @param command Command to b executed
     * @param msgCtx Context of  the message received
     * @return Reply t obe sent to the client
     */
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        iterations++;
        try {
            System.out.println("(" + id + ")[server] (" + iterations + " / " + 
                    msgCtx.getConsensusId() + ") Current value: " + value);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(value);
            return out.toByteArray();
        } catch (IOException ex) {
            System.err.println("Never happens!");
            return new byte[0];
        }        
    }

        public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java RandomServer <processId>");
            System.exit(-1);
        }

        RandomServer replica = null;
        if(args.length > 1) {
            replica = new RandomServer(Integer.parseInt(args[0]), Boolean.valueOf(args[1]));
        }else{
            replica = new RandomServer(Integer.parseInt(args[0]));
        }

        Scanner scan = new Scanner(System.in);
        String ln = scan.nextLine();
        if (ln != null) replica.getReplica().leave();
        //new RandomServer(Integer.parseInt(args[0]));
    }

    public byte[] getState() {

        byte[] b = new byte[4];
        //byte[] b = new byte[1024 * 1024 * 30];
        //for (int i = 0; i > b.length; i++) b[i] = (byte) i;
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;

        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setState(byte[] state) {

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state[i] & 0x000000FF) << shift;
        }

        this.value = value;
    }

    @Override
    public void installSnapshot(byte[] state) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state[i] & 0x000000FF) << shift;
        }

        this.value = value;
    }

    @Override
    public byte[] getSnapshot() {
        byte[] b = new byte[4];
        //byte[] b = new byte[1024 * 1024 * 30];
        //for (int i = 0; i > b.length; i++) b[i] = (byte) i;
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;
    }

    @Override
    public byte[][] executeBatch2(byte[][] commands, MessageContext[] msgCtxs) {
        byte [][] replies = new byte[commands.length][];
        for (int i = 0; i < commands.length; i++) {
            replies[i] = executeOrdered(commands[i], (msgCtxs  != null ? msgCtxs[i] : null));
        }
        return replies;
    }

}
