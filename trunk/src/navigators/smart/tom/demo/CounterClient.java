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
import navigators.smart.tom.ServiceProxy;


/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class CounterClient {

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("Usage: java CounterClient <process id> <increment>");
            System.out.println("       if <increment> equals 0 the request will be read-only");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));

        int i=0;
        int inc = Integer.parseInt(args[1]);
        //sends 1000 requests to replicas and then terminates
        while(i<50){
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(CounterClient.class.getName()).log(Level.SEVERE, null, ex);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(inc);

	        byte[] reply = counterProxy.invoke(out.toByteArray(),(inc == 0));	
	        int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();	
	        System.out.println("Counter value: "+newValue);
	        i++;
        }
        //System.exit(0);
    }

}
