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
package bftsmart.demo.multigroup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import bftsmart.communication.tls.TLSNettyCommunicationFactory;
import bftsmart.multigroup.MultiGroupReplica;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.util.TOMConfigurationBuilder;
import bftsmart.tom.MessageContext;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

/**
 * One process that takes part in TWO independent consensus groups at the same time
 * (multi-raft / multi-group), each an independent replicated counter on its own ports.
 *
 * <p>Run the same command with id 0..3 in four JVMs: each JVM hosts node {@code id} of
 * group 0 (ports 110xx) and node {@code id} of group 1 (ports 120xx). The two groups are
 * fully isolated (separate state, leaders, view storage).</p>
 *
 * <pre>java ... bftsmart.demo.multigroup.MultiGroupCounterServer &lt;id 0..3&gt;</pre>
 */
public final class MultiGroupCounterServer {

    /** ports: group 0 -> 110x0/110x1, group 1 -> 120x0/120x1. */
    private static TOMConfiguration groupConfig(int groupId, int id) {
        int base = (groupId == 0) ? 11000 : 12000;
        TOMConfigurationBuilder b = new TOMConfigurationBuilder()
                .servers(4).f(1).initialView(0, 1, 2, 3)
                .defaultKeys(true).useSignatures(false)
                .enabledCiphers("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");
        for (int i = 0; i < 4; i++) {
            b.host(i, "127.0.0.1", base + i * 10, base + i * 10 + 1);
        }
        return b.build(id);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: MultiGroupCounterServer <id 0..3>");
            System.exit(1);
        }
        int id = Integer.parseInt(args[0]);
        MultiGroupReplica mgr = new MultiGroupReplica();
        // This single process joins both groups.
        Counter g0 = new Counter(0);
        Counter g1 = new Counter(1);
        mgr.addInMemoryGroup(0, groupConfig(0, id), g0, g0, new TLSNettyCommunicationFactory());
        mgr.addInMemoryGroup(1, groupConfig(1, id), g1, g1, new TLSNettyCommunicationFactory());
        System.out.println("Node " + id + " is now part of groups " + mgr.groupIds());
    }

    /** A replicated counter tagged with its group id (for readable logs). */
    static final class Counter extends DefaultSingleRecoverable {
        private final int groupId;
        private int counter = 0;

        Counter(int groupId) {
            this.groupId = groupId;
        }

        @Override
        public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
            counter += toInt(command);
            System.out.println("[group " + groupId + "] counter = " + counter);
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

    static int toInt(byte[] b) {
        try {
            return new DataInputStream(new ByteArrayInputStream(b)).readInt();
        } catch (IOException e) {
            return 0;
        }
    }

    static byte[] toBytes(int v) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(v);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
