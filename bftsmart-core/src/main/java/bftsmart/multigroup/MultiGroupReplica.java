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
import bftsmart.communication.SharedWorkerPool;
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
 * <h3>Storage layout</h3>
 * <p>When durable state is enabled, pass a {@link GroupStorageLayout} so each group's
 * checkpoint and log files land in an isolated sub-directory and never collide:</p>
 * <pre>{@code
 * GroupStorageLayout layout = new GroupStorageLayout("/data/disk1", "/data/disk2");
 * replica.addGroup(0, conf0, exec, rec, factory, layout);  // → /data/disk1/0/
 * replica.addGroup(1, conf1, exec, rec, factory, layout);  // → /data/disk2/1/
 * }</pre>
 * <p>The durable state-transfer port is also auto-assigned per group: the first group uses
 * {@code portBase + replicaId} (default portBase = 4444), the second group uses
 * {@code portBase + 1000 + replicaId}, and so on, so no manual port configuration is needed
 * when all groups share the same JVM.</p>
 *
 * <h3>Transport layouts</h3>
 * <p><b>A1</b> ({@link #addGroup}): each group has its own replica-to-replica transport
 * (its own TLS server port and connection mesh). Simple; use when groups have different
 * memberships or when simplicity matters more than connection count.</p>
 *
 * <p><b>A2</b> ({@link #addSharedGroup}): all groups share a single replica-to-replica
 * transport multiplexed by {@code groupId} in the binary message envelope. One connection
 * per replica pair regardless of how many groups are hosted.</p>
 */
public final class MultiGroupReplica {

    private final Map<Integer, ServiceReplica> groups = new ConcurrentHashMap<>();
    private final String configHome;
    // A2 (shared transport): a single replica-to-replica transport multiplexed across groups.
    private ServerCommunicationLayer sharedServersConn = null;
    // A2 fairness: shared worker pool for fair round-robin message dispatch across A2 groups.
    // Created lazily when the first shared group is added (same pattern as sharedServersConn).
    private SharedWorkerPool sharedWorkerPool = null;

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
     * Uses {@link GroupStorageLayout#defaultLayout()} for durable state files.
     */
    public synchronized ServiceReplica addGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                Recoverable recoverer, CommunicationFactory factory) {
        return addGroup(groupId, conf, executor, recoverer, factory,
                new NamespacedFileViewStorage(configHome, Integer.toString(groupId)),
                GroupStorageLayout.defaultLayout());
    }

    /**
     * Adds and starts a consensus group with an explicit {@link GroupStorageLayout} for
     * durable state files. The layout assigns each group an isolated sub-directory and
     * balances groups across the provided base directories.
     */
    public synchronized ServiceReplica addGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                Recoverable recoverer, CommunicationFactory factory,
                                                GroupStorageLayout storageLayout) {
        return addGroup(groupId, conf, executor, recoverer, factory,
                new NamespacedFileViewStorage(configHome, Integer.toString(groupId)),
                storageLayout);
    }

    /**
     * Adds and starts an ephemeral consensus group whose view is kept in memory only
     * (tests / demos). Durable state uses {@link GroupStorageLayout#defaultLayout()}.
     */
    public synchronized ServiceReplica addInMemoryGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                        Recoverable recoverer, CommunicationFactory factory) {
        return addGroup(groupId, conf, executor, recoverer, factory,
                new InMemoryViewStorage(), GroupStorageLayout.defaultLayout());
    }

    /**
     * Adds and starts a consensus group with an explicit per-instance {@link ViewStorage}
     * and a {@link GroupStorageLayout} for durable state.
     *
     * <p>The {@code storageLayout} assigns an isolated directory to {@code groupId} and
     * sets it on {@code conf} ({@link TOMConfiguration#setStorageDir}) before the replica
     * starts, so {@code DefaultSingleRecoverable} / {@code DefaultRecoverable} pick it up
     * via {@code ReplicaContext}. The durable state-transfer port base is also auto-assigned
     * to avoid port collisions between groups in the same JVM.</p>
     */
    public synchronized ServiceReplica addGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                Recoverable recoverer, CommunicationFactory factory,
                                                ViewStorage viewStore, GroupStorageLayout storageLayout) {
        if (groups.containsKey(groupId)) {
            throw new IllegalArgumentException("Group already present: " + groupId);
        }
        applyStorageLayout(groupId, conf, storageLayout);
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
        return addSharedGroup(groupId, conf, executor, recoverer, factory,
                viewStore, GroupStorageLayout.defaultLayout());
    }

    /**
     * Adds a shared-transport group with an explicit {@link GroupStorageLayout}.
     */
    public synchronized ServiceReplica addSharedGroup(int groupId, TOMConfiguration conf, Executable executor,
                                                      Recoverable recoverer, CommunicationFactory factory,
                                                      ViewStorage viewStore,
                                                      GroupStorageLayout storageLayout) throws Exception {
        if (groups.containsKey(groupId)) {
            throw new IllegalArgumentException("Group already present: " + groupId);
        }
        applyStorageLayout(groupId, conf, storageLayout);
        if (sharedServersConn == null) {
            ServerViewController transportController = new ServerViewController(conf, new InMemoryViewStorage());
            sharedServersConn = factory.newServerCommunicationLayer(
                    transportController, new LinkedBlockingQueue<SystemMessage>(), null);
        }
        if (sharedWorkerPool == null) {
            sharedWorkerPool = new SharedWorkerPool();
        }
        ServiceReplica replica = new ServiceReplica(conf, executor, recoverer, null, null,
                factory, viewStore, groupId, sharedServersConn, sharedWorkerPool);
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

    /**
     * Shuts down the shared worker pool used by A2 (shared-transport) groups.
     * Should be called after all shared groups have been stopped (e.g. via
     * {@link bftsmart.tom.ServiceReplica#kill()}). Has no effect if no shared
     * groups were ever added.
     */
    public synchronized void shutdownSharedPool() {
        if (sharedWorkerPool != null) {
            sharedWorkerPool.shutdown();
            sharedWorkerPool = null;
        }
    }

    /**
     * Sets the per-group storage dir from the layout and auto-assigns a non-colliding
     * durable state-transfer port base (default 4444, incremented by 1000 per group slot).
     */
    private void applyStorageLayout(int groupId, TOMConfiguration conf, GroupStorageLayout layout) {
        conf.setStorageDir(layout.dirFor(groupId));
        // Auto-assign a distinct port base per group so durable state-transfer sockets
        // (port = base + replicaId) don't collide when multiple groups share a JVM.
        // The formula is deterministic on groupId so a restarting node always binds
        // the same port, regardless of the order groups are registered.
        if (conf.getStateTransferPortBase() == 4444) {
            // only override if still at the default (user hasn't set a custom base)
            conf.setStateTransferPortBase(4444 + Math.abs(groupId) * 1000);
        }
    }
}
