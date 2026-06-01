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
package bftsmart.communication.server;

import javax.crypto.SecretKey;

import bftsmart.communication.SystemMessage;

/**
 * Transport abstraction for replica-to-replica (server-to-server) communication.
 *
 * <p>This interface decouples the consensus/TOM layers from the concrete networking
 * implementation, so that a different transport (e.g. Apache Pekko) can be plugged in
 * without touching the rest of the library. The current TLS/socket based implementation
 * ({@code ServersCommunicationLayer}) lives in the {@code bftsmart-tls} module.</p>
 *
 * <p>Instances are created through a {@link bftsmart.communication.CommunicationFactory}.</p>
 */
public interface ServerCommunicationLayer {

    /**
     * Sends a system message to the given target replicas.
     *
     * @param targets the ids of the target replicas
     * @param sm the message to send
     * @param useMAC whether to authenticate the message
     */
    void send(int[] targets, SystemMessage sm, boolean useMAC);

    /**
     * Returns the secret key shared with the given replica (used to authenticate messages).
     *
     * @param id the id of the replica (or this replica's own id)
     * @return the shared secret key
     */
    SecretKey getSecretKey(int id);

    /**
     * Refreshes the set of established connections to reflect the current view.
     */
    void updateConnections();

    /**
     * Establishes connections that were pending until the join into the current view
     * was processed.
     */
    void joinViewReceived();

    /**
     * Shuts down the transport, closing every connection.
     */
    void shutdown();

    /**
     * Blocks until the transport's background processing has terminated.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    void join() throws InterruptedException;

    /**
     * Registers (or replaces) the inbound queue of a consensus group, so that this
     * (possibly shared) transport delivers messages tagged with {@code groupId} to it.
     * This is what lets several consensus groups multiplex over a single transport
     * (multi-group / multi-raft); group 0 is the default group.
     *
     * @param groupId the consensus group id
     * @param inQueue the queue receiving that group's inbound messages
     */
    void registerGroupInQueue(int groupId, java.util.concurrent.LinkedBlockingQueue<SystemMessage> inQueue);
}
