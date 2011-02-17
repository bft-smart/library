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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.io.InputStreamReader;
import navigators.smart.tom.ServiceProxy;

/**
 * Example client that updates a BFT replicated service (a counter).
 *
 */
public class CounterClient {

    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java CounterClient <process id> <increment>");
            System.out.println("       if <increment> equals 0 the request will be read-only");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));

        int i = 0;
        int inc = Integer.parseInt(args[1]);
        //sends 1000 requests to replicas and then terminates

        //******* EDUARDO BEGIN **************//
        boolean wait = true;

        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
        
        while (i < 1000) {


            if (wait) {

                System.out.println("Press Enter...");

                String lido = inReader.readLine();

                if (lido.equals("exit")) {
                    counterProxy.close();
                    break;
                }else if(lido.equals("go")) {
                    wait = false;
                }
            }


            /*try {
                Thread.currentThread().sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            //******* EDUARDO END **************//

            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(inc);

            System.out.println("Counter sending: " + i);
            byte[] reply = counterProxy.invoke(out.toByteArray(), (inc == 0));
            int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
            System.out.println("Counter value: " + newValue);
            i++;
        }
    //System.exit(0);
    }
}
