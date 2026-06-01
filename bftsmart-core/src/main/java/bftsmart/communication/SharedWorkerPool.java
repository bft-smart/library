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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A shared, bounded worker pool that fairly drains the inbound (server-to-server) queues of
 * several consensus groups hosted in one process (multi-group / multi-raft).
 *
 * <p>Without this pool each {@link ServerCommunicationSystem} runs its own dedicated thread that
 * blocks on its {@code inQueue}; hosting N groups therefore costs N processing threads and gives
 * no fairness guarantee — a single busy group can monopolise CPU. This pool replaces those N
 * dedicated threads with a fixed, bounded set of worker threads that visit every registered
 * group in <b>round-robin</b> order.</p>
 *
 * <h3>How fairness is guaranteed</h3>
 * <ul>
 *   <li>Workers iterate the registered channels in a strict round-robin cursor (a single shared
 *       {@link AtomicInteger} advanced atomically), so over time every group is visited equally
 *       often regardless of how loaded it is.</li>
 *   <li>On each visit a worker drains at most {@code drainBudget} messages from that group's queue
 *       and then yields the channel back to the rotation. A group with a deep backlog therefore
 *       cannot hold a worker hostage: after a bounded amount of work the worker moves on to the
 *       next group.</li>
 *   <li>When a full sweep finds no work at all, workers park on a condition and are woken by
 *       {@link #signalWork()} (invoked when a message is enqueued), so idle groups cost nothing.</li>
 * </ul>
 *
 * <p>The pool only handles the per-group message-processing loop; it does not own the transport
 * connections. Single-group / single-replica deployments do not use this class at all and keep
 * their original dedicated-thread behaviour unchanged.</p>
 *
 * @author multigroup
 */
public final class SharedWorkerPool {

    /**
     * A unit of fairly-scheduled work: one consensus group's drain loop. Implemented by
     * {@link ServerCommunicationSystem} so the pool stays decoupled from message processing.
     */
    public interface Channel {
        /**
         * Processes at most {@code budget} ready messages, then returns. Implementations must not
         * block waiting for new messages — they should process whatever is immediately available
         * (and may run light periodic maintenance, e.g. verifying pending messages) and return.
         *
         * @param budget the maximum number of messages to process in this visit
         * @return the number of messages actually processed (0 if the channel was idle)
         */
        int drain(int budget);

        /** @return false once the channel is shut down and should be removed from the rotation. */
        boolean isActive();
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CopyOnWriteArrayList<Channel> channels = new CopyOnWriteArrayList<>();
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final Thread[] workers;
    private final int drainBudget;
    private final long idleParkNanos;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private volatile boolean running = true;

    /**
     * Creates a shared worker pool with sensible defaults: a number of workers bounded by the
     * available processors, a per-visit drain budget of 128 messages, and a 100 ms idle park.
     */
    public SharedWorkerPool() {
        this(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())), 128, 100L);
    }

    /**
     * @param numWorkers  number of shared worker threads (bounded; reused across all groups)
     * @param drainBudget maximum messages processed per group per round-robin visit (fairness knob)
     * @param idleParkMs  how long a worker parks when a full sweep found no work
     */
    public SharedWorkerPool(int numWorkers, int drainBudget, long idleParkMs) {
        if (numWorkers < 1) {
            numWorkers = 1;
        }
        if (drainBudget < 1) {
            drainBudget = 1;
        }
        this.drainBudget = drainBudget;
        this.idleParkNanos = Math.max(1L, idleParkMs) * 1_000_000L;
        this.workers = new Thread[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            Thread t = new Thread(this::workerLoop, "SharedWorkerPool-" + i);
            t.setDaemon(true);
            workers[i] = t;
            t.start();
        }
        logger.info("SharedWorkerPool started with {} worker(s), drainBudget={}", numWorkers, drainBudget);
    }

    /** Registers a group's drain channel into the fair round-robin rotation. */
    public void register(Channel channel) {
        channels.addIfAbsent(channel);
        signalWork();
    }

    /** Removes a group's drain channel from the rotation (called on group shutdown). */
    public void deregister(Channel channel) {
        channels.remove(channel);
    }

    /**
     * Wakes a parked worker because new work may be available. Cheap to call on the message
     * enqueue path.
     */
    public void signalWork() {
        lock.lock();
        try {
            workAvailable.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Stops all workers. Channels are responsible for their own transport shutdown. */
    public void shutdown() {
        running = false;
        lock.lock();
        try {
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void workerLoop() {
        while (running) {
            int processed = 0;
            int size = channels.size();
            if (size == 0) {
                parkBriefly();
                continue;
            }
            // One fair sweep: visit each registered channel at most once, advancing the shared
            // round-robin cursor so different workers start at different groups and no group is
            // structurally favoured.
            for (int i = 0; i < size && running; i++) {
                int idx = Math.floorMod(cursor.getAndIncrement(), size);
                Channel ch;
                try {
                    ch = channels.get(idx);
                } catch (IndexOutOfBoundsException concurrentRemoval) {
                    continue; // a channel was deregistered mid-sweep; skip
                }
                if (!ch.isActive()) {
                    channels.remove(ch);
                    continue;
                }
                try {
                    processed += ch.drain(drainBudget);
                } catch (RuntimeException ex) {
                    logger.error("Error draining a group channel", ex);
                }
            }
            if (processed == 0) {
                parkBriefly();
            }
        }
        logger.info("SharedWorkerPool worker stopped.");
    }

    private void parkBriefly() {
        lock.lock();
        try {
            if (running) {
                workAvailable.awaitNanos(idleParkNanos);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
}
