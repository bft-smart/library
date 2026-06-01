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
package bftsmart.multigroup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.LinkedBlockingQueue;

import bftsmart.communication.CommunicationFactory;
import bftsmart.communication.SystemMessage;
import bftsmart.communication.server.ServerCommunicationLayer;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.InMemoryViewStorage;
import bftsmart.reconfiguration.views.NamespacedFileViewStorage;
import bftsmart.reconfiguration.views.ViewStorage;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.Executable;
import bftsmart.tom.server.Recoverable;

/**
 * Hosts several independent consensus groups (à la Apache Ratis multi-raft) inside a
 * single process. Each group is a fully isolated SMR instance — its own
 * {@link TOMConfiguration} / view, consensus sequence, leader, state machine and view
 * storage — so one server can take part in many groups (e.g. shards) at once.
 *
 * <p>This orchestrator wires each group as an independent {@link ServiceReplica} with a
 * per-instance {@link ViewStorage} (so groups never collide on the shared
 * {@code config/currentView} file) and a per-instance {@link CommunicationFactory}. With
 * distinct configurations (ports) per group this needs no shared transport. The
 * transport envelope already carries a {@code groupId} and {@code ServerCommunicationLayer}
 * supports {@code registerGroupInQueue}, which a future variant can use to multiplex all
 * groups over a single shared transport.</p>
 */
public final class MultiGroupReplica {

    private final Map<Integer, ServiceReplica> groups = new ConcurrentHashMap<>();
    private final String configHome;
    // A2 (shared transport): a single replica-to-replica transport multiplexed across groups.
    private ServerCommunicationLayer sharedServersConn = null;

    /** @param configHome directory for the per-group persistent view files (production). */
    public MultiGroupReplica(String configHome) {
        this.configHome = configHome;
    }

    public MultiGroupReplica() {
        this(null);
    }

    /**
     * Adds and starts a consensus group, persisting its view in a per-group file
     * ({@code currentView.<groupId>}) so the group survives restarts.
     */
    public synchronized ServiceReplica addGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                Recoverable recoverer, CommunicationFactory factory) {
        return addGroup(groupId, conf, executor, recoverer, factory,
                new NamespacedFileViewStorage(configHome, Integer.toString(groupId)));
    }

    /**
     * Adds and starts an ephemeral consensus group whose view is kept in memory only
     * (tests / demos).
     */
    public synchronized ServiceReplica addInMemoryGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                        Recoverable recoverer, CommunicationFactory factory) {
        return addGroup(groupId, conf, executor, recoverer, factory, new InMemoryViewStorage());
    }

    /**
     * Adds and starts a consensus group with an explicit per-instance {@link ViewStorage}.
     */
    public synchronized ServiceReplica addGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                Recoverable recoverer, CommunicationFactory factory,
                                                ViewStorage viewStore) {
        if (groups.containsKey(groupId)) {
            throw new IllegalArgumentException("Group already present: " + groupId);
        }
        ServiceReplica replica = new ServiceReplica(conf, executor, recoverer, null, null, factory, viewStore);
        groups.put(groupId, replica);
        return replica;
    }

    /**
     * Adds a group that shares a single replica-to-replica transport with the other
     * shared groups (A2 densification: one TLS server-to-server port and one connection
     * mesh for all groups, demultiplexed by groupId). All shared groups must have the
     * same membership/server-to-server ports (the common multi-shard case); they may use
     * distinct client ports. The shared transport is created lazily from the first group's
     * configuration.
     */
    public synchronized ServiceReplica addSharedGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                      Recoverable recoverer, CommunicationFactory factory,
                                                      ViewStorage viewStore) throws Exception {
        if (groups.containsKey(groupId)) {
            throw new IllegalArgumentException("Group already present: " + groupId);
        }
        if (sharedServersConn == null) {
            // Build the shared transport once, from the (membership-equivalent) first group.
            ServerViewController transportController = new ServerViewController(conf, new InMemoryViewStorage());
            sharedServersConn = factory.newServerCommunicationLayer(
                    transportController, new LinkedBlockingQueue<SystemMessage>(), null);
        }
        ServiceReplica replica = new ServiceReplica(conf, executor, recoverer, null, null,
                factory, viewStore, groupId, sharedServersConn);
        groups.put(groupId, replica);
        return replica;
    }

    /** @return the {@link ServiceReplica} hosting the given group, or {@code null}. */
    public ServiceReplica group(int groupId) {
        return groups.get(groupId);
    }

    /** @return the ids of the groups currently hosted. */
    public java.util.Set<Integer> groupIds() {
        return groups.keySet();
    }
}
