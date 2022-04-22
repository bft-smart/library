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
package bftsmart.demo.counter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import bftsmart.forensic.AuditResult;
import bftsmart.forensic.AuditStorage;
import bftsmart.forensic.Auditor;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.TOMUtil;

/**
 * Example client that updates a BFT replicated service (a counter).
 * 
 * @author alysson
 */
public class CounterClientForensics {

    private static final int FORENSICSTHRESHOLD = 2; // forensics frequency

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ... CounterClient <process id> <increment> [<number of operations>]");
            System.out.println("       if <increment> equals 0 the request will be read-only");
            System.out.println("       default <number of operations> equals 1000");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));

        try {

            int inc = Integer.parseInt(args[1]);
            int numberOfOps = (args.length > 2) ? Integer.parseInt(args[2]) : 1000;

            for (int i = 0; i < numberOfOps; i++) {

                ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                new DataOutputStream(out).writeInt(inc);

                System.out.print("Invocation " + i);
                byte[] reply = (inc == 0) ? counterProxy.invokeUnordered(out.toByteArray())
                        : counterProxy.invokeOrdered(out.toByteArray()); // magic happens here
                if (reply != null) {
                    int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
                    System.out.println(", returned value: " + newValue);
                } else {
                    System.out.println(", ERROR! Exiting.");
                    break;
                }

                // Forensics
                // if (i != 0 && i % FORENSICSTHRESHOLD == 0) {
                //     performForensics(counterProxy, i - FORENSICSTHRESHOLD, i);
                // }
                performForensics(counterProxy, i, i); // does forensisc every consensus id
            }
        } catch (IOException | NumberFormatException e) {
            counterProxy.close();
        }
    }

    /*************************** FORENSICS METHODS *******************************/

    /**
     * Invokes Audit, receives responces and performs forensics
     * TODO implement forensics only between low and high views
     * @param counterProxy
     * @param low
     * @param high
     * @throws IOException
     */
    private static void performForensics(ServiceProxy counterProxy, int low, int high) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        DataOutputStream doutstream = new DataOutputStream(out);
        doutstream.writeInt(low);
        doutstream.writeInt(high);
        System.out.println("### Forensics start ###");// from consensus " + low + " to " + high);

        byte[] reply = counterProxy.invoke(out.toByteArray(), TOMMessageType.AUDIT);
        
        try {
            List<AuditStorage> storages = (List<AuditStorage>) TOMUtil.getObject(reply);

            Auditor audit = new Auditor();
            AuditResult result = audit.audit(storages);

            if (result.conflictFound()) {
                System.out.println(result);
            } else {
                System.out.println("No conflict found");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("### Forensics complete ###");
    }

    // private static List<AuditResponse> readAuditResponses(byte[] reply) {
    //     try {
    //         ByteArrayInputStream bis = new ByteArrayInputStream(reply); 
    //         ObjectInputStream ois = new ObjectInputStream(bis);
    //         byte[][] replies = (byte[][]) ois.readObject();
    //         List<AuditResponse> responses = new ArrayList<>();
    //         List<TOMMessage> tms = new ArrayList<>();
    //         for (byte[] rep : replies) {
    //             tms.add(TOMMessage.bytesToMessage(rep));
    //         }
    //         // System.out.println("Messages inside");
    //         for (TOMMessage tomMessage : tms) {
    //             // System.out.println(tomMessage);
    //             byte[] content = tomMessage.getContent(); // this content will be the Audit Storage
    //             bis = new ByteArrayInputStream(content);
    //             ois = new ObjectInputStream(bis);
    //             responses.add((AuditResponse) ois.readObject());
    //         }
    //         return responses;
    //     } catch (Exception e) {
    //         System.out.println();
    //     }
    //     return null;
    // }
}
