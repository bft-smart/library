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
package bftsmart.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.server.ReplicaConnection;
import bftsmart.communication.server.ServerCommunicationLayer;
import bftsmart.communication.server.StateTransferSender;
import bftsmart.reconfiguration.ClientViewController;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.ServiceReplica;

/**
 * Abstract factory for every networking component used by BFT-SMaRt.
 *
 * <p>This is the single seam between the protocol layers (which live in the
 * {@code bftsmart-core} module) and a concrete transport implementation (which lives in
 * a separate module, e.g. {@code bftsmart-tls} for the default TLS/Netty stack, or a
 * future {@code bftsmart-pekko} module). Wiring is done programmatically: the application
 * creates the desired factory and either passes it to the relevant constructor or registers
 * it once through {@link CommunicationFactoryProvider#setDefaultFactory(CommunicationFactory)}.</p>
 */
public interface CommunicationFactory {

    /**
     * Creates the replica-to-replica transport layer.
     *
     * @param controller the server view controller
     * @param inQueue the queue into which received messages are delivered
     * @param replica the owning replica (may be {@code null} for tooling)
     * @return a new {@link ServerCommunicationLayer}
     * @throws Exception if the transport cannot be initialized
     */
    ServerCommunicationLayer newServerCommunicationLayer(ServerViewController controller,
                                                         LinkedBlockingQueue<SystemMessage> inQueue,
                                                         ServiceReplica replica) throws Exception;

    /**
     * Creates the server side of the client-to-server communication system.
     *
     * @param controller the server view controller
     * @return a new {@link CommunicationSystemServerSide}
     */
    CommunicationSystemServerSide newCommunicationSystemServerSide(ServerViewController controller);

    /**
     * Creates the client side of the client-to-server communication system.
     *
     * @param clientId the client process id
     * @param controller the client view controller
     * @return a new {@link CommunicationSystemClientSide}
     */
    CommunicationSystemClientSide newCommunicationSystemClientSide(int clientId, ClientViewController controller);

    /**
     * Creates a one-shot connection to a single remote replica, used by reconfiguration code.
     *
     * @param controller the server view controller
     * @param remoteId the id of the remote replica
     * @return a new {@link ReplicaConnection}
     */
    ReplicaConnection newReplicaConnection(ServerViewController controller, int remoteId);

    /**
     * Creates the provider endpoint of the bulk state-transfer channel (durable state
     * manager). The returned endpoint listens at {@code bindAddress} and, when the
     * recovering replica connects, streams the {@link ApplicationState} produced by
     * {@code stateSupplier} (evaluated lazily, at connection time).
     *
     * @param bindAddress the address/port the provider listens on
     * @param stateSupplier supplies the state to stream once a peer connects
     * @return a new {@link StateTransferSender}
     */
    StateTransferSender newStateTransferSender(InetSocketAddress bindAddress,
                                               Supplier<ApplicationState> stateSupplier);

    /**
     * Receive side of the bulk state-transfer channel: connects to a provider endpoint
     * and reads a single {@link ApplicationState}.
     *
     * @param address the provider endpoint advertised by the state provider
     * @return the received application state
     * @throws IOException if the state cannot be fetched or deserialized
     */
    ApplicationState fetchState(InetSocketAddress address) throws IOException;
}
