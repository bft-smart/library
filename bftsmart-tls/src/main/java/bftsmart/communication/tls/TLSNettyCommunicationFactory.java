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
package bftsmart.communication.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import bftsmart.communication.CommunicationFactory;
import bftsmart.communication.SystemMessage;
import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.netty.NettyClientServerCommunicationSystemClientSide;
import bftsmart.communication.client.netty.NettyClientServerCommunicationSystemServerSide;
import bftsmart.communication.server.ReplicaConnection;
import bftsmart.communication.server.ServerCommunicationLayer;
import bftsmart.communication.server.ServerConnection;
import bftsmart.communication.server.ServersCommunicationLayer;
import bftsmart.communication.server.StateTransferSender;
import bftsmart.reconfiguration.ClientViewController;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.ServiceReplica;

/**
 * Default {@link CommunicationFactory} implementation: the historical BFT-SMaRt transport
 * built on TLS server sockets (replica-to-replica) and Netty (client-to-server).
 *
 * <p>This is the concrete networking stack of the {@code bftsmart-tls} module. To use a
 * different transport (e.g. Apache Pekko), provide another {@link CommunicationFactory}
 * implementation and wire it in instead of this one.</p>
 */
public class TLSNettyCommunicationFactory implements CommunicationFactory {

    /**
     * Convenience for composition roots (e.g. {@code main} methods of replicas/clients):
     * registers a {@code TLSNettyCommunicationFactory} as the default transport so that
     * entry points constructed without an explicit factory use the TLS/Netty stack.
     */
    public static void installAsDefault() {
        bftsmart.communication.CommunicationFactoryProvider.setDefaultFactory(new TLSNettyCommunicationFactory());
    }

    @Override
    public ServerCommunicationLayer newServerCommunicationLayer(ServerViewController controller,
                                                                LinkedBlockingQueue<SystemMessage> inQueue,
                                                                ServiceReplica replica) throws Exception {
        return new ServersCommunicationLayer(controller, inQueue, replica);
    }

    @Override
    public CommunicationSystemServerSide newCommunicationSystemServerSide(ServerViewController controller) {
        return new NettyClientServerCommunicationSystemServerSide(controller);
    }

    @Override
    public CommunicationSystemClientSide newCommunicationSystemClientSide(int clientId, ClientViewController controller) {
        return new NettyClientServerCommunicationSystemClientSide(clientId, controller);
    }

    @Override
    public ReplicaConnection newReplicaConnection(ServerViewController controller, int remoteId) {
        return new ServerConnection(controller, null, remoteId, null, null);
    }

    @Override
    public StateTransferSender newStateTransferSender(InetSocketAddress bindAddress,
                                                      Supplier<ApplicationState> stateSupplier) {
        return SocketStateTransfer.serve(bindAddress, stateSupplier);
    }

    @Override
    public ApplicationState fetchState(InetSocketAddress address) throws IOException {
        return SocketStateTransfer.fetch(address);
    }
}
