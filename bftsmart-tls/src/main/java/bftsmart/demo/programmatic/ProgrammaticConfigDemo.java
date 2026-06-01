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
package bftsmart.demo.programmatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import bftsmart.communication.tls.TLSNettyCommunicationFactory;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.util.TOMConfigurationBuilder;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

/**
 * Counter demo configured <b>purely programmatically</b>: it does NOT read
 * {@code system.config} or {@code hosts.config} from disk. The whole configuration is
 * built in memory with {@link TOMConfigurationBuilder} and injected into
 * {@link ServiceReplica} / {@link ServiceProxy}, together with a transport
 * {@code CommunicationFactory}.
 *
 * <p>The only on-disk material still required is the TLS keystore (crypto material under
 * {@code config/keysSSL_TLS/}) used by the default TLS transport, plus the JVM-level
 * {@code java.security} / logging files — none of which are BFT-SMaRt configuration files.</p>
 *
 * <p>Usage (4 replicas + a client), from a directory that contains only
 * {@code config/keysSSL_TLS/} (no system.config / hosts.config):</p>
 * <pre>
 *   java ... bftsmart.demo.programmatic.ProgrammaticConfigDemo server 0
 *   java ... bftsmart.demo.programmatic.ProgrammaticConfigDemo server 1
 *   java ... bftsmart.demo.programmatic.ProgrammaticConfigDemo server 2
 *   java ... bftsmart.demo.programmatic.ProgrammaticConfigDemo server 3
 *   java ... bftsmart.demo.programmatic.ProgrammaticConfigDemo client 1001 &lt;increment&gt; &lt;ops&gt;
 * </pre>
 */
public class ProgrammaticConfigDemo {

    /** Builds the in-memory configuration for the given process id. */
    private static TOMConfiguration config(int id) {
        return new TOMConfigurationBuilder()
                .servers(4).f(1).initialView(0, 1, 2, 3)
                .defaultKeys(true).useSignatures(false)
                .enabledCiphers("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
                .host(0, "127.0.0.1", 11000, 11001)
                .host(1, "127.0.0.1", 11010, 11011)
                .host(2, "127.0.0.1", 11020, 11021)
                .host(3, "127.0.0.1", 11030, 11031)
                .build(id);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !(args[0].equals("server") || args[0].equals("client"))) {
            System.out.println("Usage: ProgrammaticConfigDemo server <id>");
            System.out.println("       ProgrammaticConfigDemo client <id> <increment> <ops>");
            System.exit(1);
        }

        if (args[0].equals("server")) {
            int id = Integer.parseInt(args[1]);
            Counter counter = new Counter();
            new ServiceReplica(config(id), counter, counter, null, null,
                    new TLSNettyCommunicationFactory());
        } else {
            int id = Integer.parseInt(args[1]);
            int increment = Integer.parseInt(args[2]);
            int ops = Integer.parseInt(args[3]);
            ServiceProxy proxy = new ServiceProxy(config(id), null, null,
                    new TLSNettyCommunicationFactory());
            for (int i = 0; i < ops; i++) {
                byte[] reply = proxy.invokeOrdered(toBytes(increment));
                System.out.println("Invocation " + i + ", returned value: " + toInt(reply));
            }
            proxy.close();
            System.exit(0);
        }
    }

    /** Minimal replicated counter (same behaviour as the classic counter demo). */
    private static final class Counter extends DefaultSingleRecoverable {
        private int counter = 0;

        @Override
        public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
            counter += toInt(command);
            System.out.println("Counter was incremented. Current value = " + counter);
            return toBytes(counter);
        }

        @Override
        public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
            return toBytes(counter);
        }

        @Override
        public byte[] getSnapshot() {
            return toBytes(counter);
        }

        @Override
        public void installSnapshot(byte[] state) {
            counter = toInt(state);
        }
    }

    private static int toInt(byte[] b) {
        try {
            return new DataInputStream(new ByteArrayInputStream(b)).readInt();
        } catch (IOException e) {
            return 0;
        }
    }

    private static byte[] toBytes(int v) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(v);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
