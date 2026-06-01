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

import bftsmart.communication.tls.TLSNettyCommunicationFactory;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.util.TOMConfigurationBuilder;
import bftsmart.reconfiguration.views.InMemoryViewStorage;
import bftsmart.tom.ServiceProxy;

/**
 * Client for a specific group of {@link MultiGroupCounterServer}.
 *
 * <pre>java ... MultiGroupCounterClient &lt;groupId 0|1&gt; &lt;clientId&gt; &lt;increment&gt; &lt;ops&gt;</pre>
 */
public final class MultiGroupCounterClient {

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

    public static void main(String[] args) throws Exception {
        int groupId = Integer.parseInt(args[0]);
        int clientId = Integer.parseInt(args[1]);
        int increment = Integer.parseInt(args[2]);
        int ops = Integer.parseInt(args[3]);

        ServiceProxy proxy = new ServiceProxy(groupConfig(groupId, clientId), null, null,
                new TLSNettyCommunicationFactory(), new InMemoryViewStorage());
        for (int i = 0; i < ops; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(increment);
            byte[] reply = proxy.invokeOrdered(out.toByteArray());
            int v = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
            System.out.println("[group " + groupId + "] invocation " + i + " -> " + v);
        }
        proxy.close();
        System.exit(0);
    }
}
