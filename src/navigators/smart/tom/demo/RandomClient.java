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
import java.util.Random;
import navigators.smart.tom.ServiceProxy;

/**
 *
 * @author Joao Sousa
 */
public class RandomClient {

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("Usage: java RandomClient <process id> <seed>");
            System.exit(-1);
        }

        int id = Integer.parseInt(args[0]);
        ServiceProxy randomProxy = new ServiceProxy(id);
        Random generator = new Random(Long.parseLong(args[1]));

        int i=0;
        //sends 1000 requests to replicas and then terminates
        while(true){

            int argument = generator.nextInt(10000) + 1;
            int operator = generator.nextInt(4);

            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(argument);
            new DataOutputStream(out).writeInt(operator);

	    byte[] reply = randomProxy.invoke(out.toByteArray(),false);
            int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
            System.out.println("(" + id + ") Current value: "+newValue);
	    i++;
        }
        //System.exit(0);
    }
}
