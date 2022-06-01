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
import java.util.concurrent.TimeUnit;

import bftsmart.communication.client.ReplyListener;
import bftsmart.correctable.Consistency;
import bftsmart.correctable.Correctable;
import bftsmart.correctable.CorrectableState;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.RequestContext;
import bftsmart.tom.core.messages.TOMMessage;

/**
 * Example client that updates a BFT replicated service (a counter).
 * 
 * @author alysson
 */
public class CounterClientICG {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ... CounterClient <process id> <increment> [<number of operations>]");
            System.out.println("       if <increment> equals 0 the request will be read-only");
            System.out.println("       default <number of operations> equals 1000");
            System.exit(-1);
        }

        AsynchServiceProxy counterProxy = new AsynchServiceProxy(Integer.parseInt(args[0]));
        Consistency levels[] = { Consistency.NONE , Consistency.WEAK, Consistency.LINE, Consistency.FINAL};

        try {

            int inc = Integer.parseInt(args[1]);
            int numberOfOps = (args.length > 2) ? Integer.parseInt(args[2]) : 1000;

            for (int i = 0; i < numberOfOps; i++) {

                ByteArrayOutputStream out = new ByteArrayOutputStream(4);
                new DataOutputStream(out).writeInt(inc);

                System.out.print("Invocation " + i);

                // byte[] reply = (inc == 0) ? counterProxy.invokeUnordered(out.toByteArray())
                // : counterProxy.invokeOrdered(out.toByteArray()); // magic happens here

                Correctable cor = counterProxy.invokeCorrectable(out.toByteArray(), levels);
                cor.setCallbacks(new ReplyListener() { // update listener
                    int level_index = 0;
                    @Override
                    public void reset() {
                    }

                    @Override
                    public void replyReceived(RequestContext context, TOMMessage reply) {
                        System.out.println("UPDATE on Correctable with consistency = " + levels[level_index++]);
                        try {
                            System.out.println("\tCurrent Value = " + new DataInputStream(new ByteArrayInputStream(cor.getValue())).readInt());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }, new ReplyListener() { // final listener

                    @Override
                    public void reset() {
                    }

                    @Override
                    public void replyReceived(RequestContext context, TOMMessage reply) {
                        System.out.println("FINAL on Correctable");
                        try {
                            System.out.println("Final Value = " + new DataInputStream(new ByteArrayInputStream(cor.getValue())).readInt());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                });

                while (cor.getState().equals(CorrectableState.UPDATING)) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("Correctable reach FINAL state, starting next iteration...");
            }
        } catch (IOException | NumberFormatException e) {
            counterProxy.close();
        }
    }

    // private class onUpdateListener implements ReplyListener {

    //     int responces = 0;
    //     double votes = 0.0;
    //     AsynchServiceProxy proxy;
    //     Consistency levels[];
    //     int level_index = 0;

    //     public onUpdateListener(AsynchServiceProxy proxy, Consistency levels[]) {
    //         this.proxy = proxy;
    //         this.levels = levels;
    //     }

    //     @Override
    //     public void reset() {
    //         System.out.println("On update Listener reset");
    //         responces = 0;
    //         votes = 0;
    //     }

    //     @Override
    //     public void replyReceived(RequestContext context, TOMMessage reply) {
    //         System.out.println("on update Listener reply received");

    //         responces++;
    //         int sender = reply.getSender();
    //         votes += proxy.getViewManager().getCurrentView().getWeight(sender);

    //         View view = proxy.getViewManager().getCurrentView();
    //         int delta = view.getDelta();
    //         int t = view.getF();
    //         double Vmax = 1.0 + (double) delta / (double) t;
    //         System.out.println("Vmax = " + Vmax);
    //         int N = view.getN();
    //         int T = (N - 1) / 3;

    //         double q = calculateConsistencyQ(levels[level_index], (double) t, Vmax);

    //         System.out.println("On Update Listener must wait for " + q + " votes; consistency_level = " + levels[0]);

    //         if (votes >= q) {
    //             if (levels[level_index].equals(Consistency.FINAL)) {
    //                 int needed_responces = (int) Math.ceil((N + 2 * T - t + 1) / 2.0);
    //                 if (votes >= q && responces > needed_responces) { // received weights votes and confirmations
    //                     System.out.println("On Update Listener received enouch replies and confirmations");
    //                     proxy.cleanAsynchRequest(context.getOperationId());
    //                 }
    //             } else {
    //                 System.out.println("On Update Listener received enouch replies");
    //                 level_index++;
    //             }
    //         }
    //         if (level_index > levels.length) {
    //             proxy.cleanAsynchRequest(context.getOperationId());
    //         }
    //     }
    // }

    private double calculateConsistencyQ(Consistency level, double t, double Vmax) {
        if (level.equals(Consistency.NONE)) {
            return 1.0;
        }
        if (level.equals(Consistency.WEAK)) {
            return t * Vmax + 1.0;

        } else if (level.equals(Consistency.LINE)) {
            return 2.0 * t * Vmax + 1.0;

        } else if (level.equals(Consistency.FINAL)) {
            // need further confirmations (N+2T-(t+1)+1)/2 responces
            return 2.0 * t * Vmax + 1.0;
        }
        System.out.println("Consistency problem, could not calculate Quorum votes");
        return 0.0; // should never reach here
    }
}
