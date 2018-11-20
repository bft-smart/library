/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.demo.microbenchmarks;

import java.io.IOException;
import java.util.Arrays;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.RequestContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 * @author anogueira
 *
 */
public class AsyncLatencyClient {

    static int initId;
    
    public static void main(String[] args) throws IOException {
        if (args.length < 8) {
            System.out.println("Usage: ... ThroughputLatencyClient <initial client id> <number of clients> <number of operations> <request size> <max interval (ms)> <read only?> <verbose?> <nosig | default | ecdsa>");
            System.exit(-1);
        }

        initId = Integer.parseInt(args[0]);
        int numThreads = Integer.parseInt(args[1]);
        int numberOfOps = Integer.parseInt(args[2]);
        int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[4]);
        boolean readOnly = Boolean.parseBoolean(args[5]);
        boolean verbose = Boolean.parseBoolean(args[6]);
        String sign = args[7];
        
        int s = 0;
        if (!sign.equalsIgnoreCase("nosig")) s++;
        if (sign.equalsIgnoreCase("ecdsa")) s++;
        
        if (s == 2 && Security.getProvider("SunEC") == null) {
            
            System.out.println("Option 'ecdsa' requires SunEC provider to be available.");
            System.exit(0);
        }
        
        Client[] clients = new Client[numThreads];

        for (int i = 0; i < numThreads; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            System.out.println("Launching client " + (initId + i));
            clients[i] = new AsyncLatencyClient.Client(initId + i, numberOfOps, requestSize, interval, readOnly, verbose, s);
        }
        
        ExecutorService exec = Executors.newFixedThreadPool(clients.length);
        Collection<Future<?>> tasks = new LinkedList<>();
        
        for (Client c : clients) {
            tasks.add(exec.submit(c));
        }
        
        // wait for tasks completion
        for (Future<?> currTask : tasks) {
            try {
                currTask.get();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
    
        exec.shutdown();
        
        System.out.println("All clients done.");

    }

    static class Client extends Thread {

        int id;
        AsynchServiceProxy serviceProxy;
        int numberOfOps;
        int requestSize;
        int interval;
        byte[] request;
        TOMMessageType reqType;
        boolean verbose;
        Random rand;
        int rampup = 3000;

        public Client(int id, int numberOfOps, int requestSize, int interval, boolean readOnly, boolean verbose, int sign) {

            this.id = id;
            this.serviceProxy = new AsynchServiceProxy(id);

            this.numberOfOps = numberOfOps;
            this.requestSize = requestSize;
            this.interval = interval;
            this.request = new byte[requestSize];
            this.reqType = (readOnly ? TOMMessageType.UNORDERED_REQUEST : TOMMessageType.ORDERED_REQUEST);
            this.verbose = verbose;
            this.request = new byte[this.requestSize];
            
            rand = new Random(System.nanoTime() + this.id);
            rand.nextBytes(request);
            
            byte[] signature = new byte[0];
            Signature eng;
            
            try {

                if (sign > 0) {

                    if (sign == 1) {
                        eng = TOMUtil.getSigEngine();
                        eng.initSign(serviceProxy.getViewManager().getStaticConf().getPrivateKey());
                    } else {

                        eng = Signature.getInstance("SHA256withECDSA", "SunEC");

                        KeyFactory kf = KeyFactory.getInstance("EC", "SunEC");
                        Base64.Decoder b64 = Base64.getDecoder();
                        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(b64.decode(ThroughputLatencyClient.privKey));
                        eng.initSign(kf.generatePrivate(spec));

                    }
                    eng.update(request);
                    signature = eng.sign();
                }

                ByteBuffer buffer = ByteBuffer.allocate(request.length + signature.length + (Integer.BYTES * 2));
                buffer.putInt(request.length);
                buffer.put(request);
                buffer.putInt(signature.length);
                buffer.put(signature);
                this.request = buffer.array();


            } catch (NoSuchAlgorithmException | SignatureException | NoSuchProviderException | InvalidKeyException | InvalidKeySpecException ex) {
                ex.printStackTrace();
                System.exit(0);
            }

        }

        public void run() {

            try {

                Storage st = new Storage(this.numberOfOps / 2);
                
                if (this.verbose) System.out.println("Executing experiment for " + this.numberOfOps + " ops");

                for (int i = 0; i < this.numberOfOps; i++) {
                    
                    long last_send_instant = System.nanoTime();
                    this.serviceProxy.invokeAsynchRequest(this.request, new ReplyListener() {

                        private int replies = 0;

                        @Override
                        public void reset() {

                            if (verbose) System.out.println("[RequestContext] The proxy is re-issuing the request to the replicas");
                            replies = 0;
                        }

                        @Override
                        public void replyReceived(RequestContext context, TOMMessage reply) {
                            StringBuilder builder = new StringBuilder();
                            builder.append("[RequestContext] id: " + context.getReqId() + " type: " + context.getRequestType());
                            builder.append("[TOMMessage reply] sender id: " + reply.getSender() + " Hash content: " + Arrays.toString(reply.getContent()));
                            if (verbose) System.out.println(builder.toString());

                            replies++;

                            double q = Math.ceil((double) (serviceProxy.getViewManager().getCurrentViewN() + serviceProxy.getViewManager().getCurrentViewF() + 1) / 2.0);

                            if (replies >= q) {
                                if (verbose) System.out.println("[RequestContext] clean request context id: " + context.getReqId());
                                serviceProxy.cleanAsynchRequest(context.getOperationId());
                            }
                        }
                    }, this.reqType);
                    if (i > (this.numberOfOps / 2)) st.store(System.nanoTime() - last_send_instant);

                    if (this.interval > 0 || this.rampup > 0) {
                        Thread.sleep(Math.max(rand.nextInt(this.interval) + 1, this.rampup));
                        if (this.rampup > 0) this.rampup -= 100;
                    }
                    
                    if (this.verbose) System.out.println("Sending " + (i + 1) + "th op");
                }

                Thread.sleep(100);//wait 100ms to receive the last replies
                
                if(this.id == initId) {
                   System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = " + st.getAverage(true) / 1000 + " us ");
                   System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + st.getDP(true) / 1000 + " us ");
                   System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = " + st.getAverage(false) / 1000 + " us ");
                   System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
                   System.out.println(this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + st.getMax(false) / 1000 + " us ");
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                this.serviceProxy.close();
            }

        }

    }
}
