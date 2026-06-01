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

import bftsmart.communication.tls.TLSNettyCommunicationFactory;
import bftsmart.multigroup.MultiGroupReplica;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.util.TOMConfigurationBuilder;
import bftsmart.reconfiguration.views.InMemoryViewStorage;

/**
 * Two consensus groups multiplexed over a SINGLE shared replica-to-replica transport
 * (A2): every node binds ONE server-to-server port (11001+id*10) shared by both groups;
 * messages are demultiplexed by their envelope groupId. Each group keeps its own client
 * port (group 0 -> 110x0, group 1 -> 120x0).
 *
 * <pre>java ... SharedTransportMultiGroupServer &lt;id 0..3&gt;</pre>
 */
public final class SharedTransportMultiGroupServer {

    /** Both groups share the server-to-server port; client ports differ per group. */
    static TOMConfiguration cfg(int groupId, int id) {
        int clientBase = (groupId == 0) ? 11000 : 12000;
        TOMConfigurationBuilder b = new TOMConfigurationBuilder()
                .servers(4).f(1).initialView(0, 1, 2, 3)
                .defaultKeys(true).useSignatures(false)
                .enabledCiphers("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");
        for (int i = 0; i < 4; i++) {
            b.host(i, "127.0.0.1", clientBase + i * 10, 11001 + i * 10); // shared S2S port
        }
        return b.build(id);
    }

    public static void main(String[] args) throws Exception {
        int id = Integer.parseInt(args[0]);
        MultiGroupReplica mgr = new MultiGroupReplica();
        MultiGroupCounterServer.Counter g0 = new MultiGroupCounterServer.Counter(0);
        MultiGroupCounterServer.Counter g1 = new MultiGroupCounterServer.Counter(1);
        mgr.addSharedGroup(0, cfg(0, id), g0, g0, new TLSNettyCommunicationFactory(), new InMemoryViewStorage());
        mgr.addSharedGroup(1, cfg(1, id), g1, g1, new TLSNettyCommunicationFactory(), new InMemoryViewStorage());
        System.out.println("Node " + id + " hosts groups " + mgr.groupIds() + " over ONE shared S2S transport");
    }
}
