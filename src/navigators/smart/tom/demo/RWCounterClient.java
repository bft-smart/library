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

import java.util.Comparator;
import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Extractor;
import navigators.smart.tom.util.Logger;

/**
 * Example client that updates a BFT replicated service (a counter).
 * In this case in particular, the client increments the counter by 1 in
 * the odd iterations (1,3,...) and read its value (using the read-only
 * optimization) in the even iteractions.
 *
 * This example also shows how to use a Comparator and Extractor to implement
 * custom processing of the reply voting.
 *
 * This client should be used with the CounterServer.
 *
 * @author alysson
 */
public class RWCounterClient {

    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java RWCounterClient <process id> <iteractions>");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]),"config",
                new VerboseComparator(), new VerboseExtractor());

        java.util.logging.Logger l = java.util.logging.Logger.getLogger(Thread.currentThread().getName());

        int iteractions = Integer.parseInt(args[1]);

        for(int i=0; i<iteractions; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(1);//i%2
            
            //System.out.println(((i%2 == 0)?"(read-only)":"")+" requesting operation "+i+" on counter.");
            byte[] reply = counterProxy.invoke(out.toByteArray(),true);
//(i%2 == 0)
            int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
            Logger.println2(l,"Counter value = " + newValue);
        }
        Logger.println2(l,"FINISHED "+Thread.currentThread().getName());
    }

    static class VerboseComparator implements Comparator<byte[]> {
        @Override
        public int compare(byte[] o1, byte[] o2) {
            try{
                int o1v = new DataInputStream(new ByteArrayInputStream(o1)).readInt();
                int o2v = new DataInputStream(new ByteArrayInputStream(o2)).readInt();
                System.out.println(Thread.currentThread().getName()+": comparing "+o1v+" and "+o2v);
                return o1v == o2v?0:-1;
            } catch(IOException ioe) {
                return -1;
            }
        }
    }

    static class VerboseExtractor implements Extractor {
        @Override
        public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {
            System.out.print(Thread.currentThread().getName()+": Received replies = { ");

            for(TOMMessage reply:replies) {
                if(reply == null)
                    continue;

                try {
                    int v = new DataInputStream(new ByteArrayInputStream(reply.getContent())).readInt();
                    System.out.print(v+" ");
                } catch (IOException ioe) {}
            }

            System.out.println("}");
            System.out.println(Thread.currentThread().getName()+": # replies with the same content = "+sameContent);

            return replies[lastReceived];
        }

    }
}
