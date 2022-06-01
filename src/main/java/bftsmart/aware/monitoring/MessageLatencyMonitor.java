package bftsmart.aware.monitoring;

import bftsmart.reconfiguration.ServerViewController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class allows to store and receive a replica's own  monitoring information. Note that all measurement data in here
 * is viewed by the perspective of what a single replica has measured recently by itself without a guarantee to be synchronized yet
 *
 * @author cb
 */
public class MessageLatencyMonitor {

    private int window;
    private ServerViewController controller;
    private TreeMap<Integer, Integer> sentMsgChallenges;

    private ArrayList<TreeMap<Integer, Long>> sentTimestamps;
    private ArrayList<TreeMap<Integer, Long>> recvdTimestamps;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    /**
     * Creates a new instance of a latency monitor
     *
     * @param controller server view controller
     */
    public MessageLatencyMonitor(ServerViewController controller) {
        this.window = controller.getStaticConf().getMonitoringWindow();
        this.controller = controller;
        init();
    }

    private synchronized void init() {
        int n = controller.getCurrentViewN();
        this.sentTimestamps = new ArrayList<>();
        this.recvdTimestamps = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            sentTimestamps.add(i, new TreeMap<>());
            recvdTimestamps.add(i, new TreeMap<>());

        }
        sentMsgChallenges = new TreeMap<>();  // Todo only in BFT
    }

    /**
     * Adds a sent timestamp
     *
     * @param replicaID            receiver
     * @param monitoringInstanceID id
     * @param timestamp            time
     */
    public synchronized void addSentTime(int replicaID, int monitoringInstanceID, Long timestamp) {
        // Clear old received timestamp from last monitoring window
        Map<Integer, Long> lastWindowRcvd = recvdTimestamps.get(replicaID);
        lastWindowRcvd.remove(monitoringInstanceID);

        this.sentTimestamps.get(replicaID).put(monitoringInstanceID % window, timestamp);
    }

    /**
     * Adds a sent timestamp with a challenge
     *
     * @param replicaID            receiver
     * @param monitoringInstanceID id
     * @param timestamp            time
     */
    public synchronized void addSentTime(int replicaID, int monitoringInstanceID, Long timestamp, int challenge) {
        this.addSentTime(replicaID, monitoringInstanceID, timestamp);

        // Todo only in BFT:
        this.sentMsgChallenges.put(monitoringInstanceID % window, challenge);
    }


    /**
     * Adds a received timestamp
     *
     * @param replicaID            sender
     * @param monitoringInstanceID id
     * @param timestamp            time
     */
    public synchronized void addRecvdTime(int replicaID, int monitoringInstanceID) {
        // Only add a response message timestamp if there is a corresponding sent message
        if (this.sentTimestamps.get(replicaID).get(monitoringInstanceID % window) != null) { //
            this.recvdTimestamps.get(replicaID).put(monitoringInstanceID % window, System.nanoTime());
        }
    }

    /**
     * Adds a received timestamp and checks for valid challenge ... ONLY IN BFT
     *
     * @param replicaID            sender
     * @param monitoringInstanceID id
     * @param timestamp            time
     */
    public synchronized void addRecvdTime(int replicaID, int monitoringInstanceID, int challenge) {
        // Only add a response message timestamp if there is a corresponding sent message AND challenge was included in response
        Integer sentChallenge = this.sentMsgChallenges.get(monitoringInstanceID % window);
        if (sentChallenge != null && sentChallenge == challenge) {
            this.addRecvdTime(replicaID, monitoringInstanceID);
        } else {
           logger.warn(challenge +" " +monitoringInstanceID  + " does not EQUAL Expected " + this.sentMsgChallenges.get(monitoringInstanceID % window));
           // should never reach here?
        }
    }

    /**
     * Creates a latency vector from the current replicas perspective
     *
     * @return latencies to all other nodes
     */
    public synchronized Long[] create_L(String description) {
        long start = System.nanoTime();
        int n = controller.getCurrentViewN();

        // Initialize latency vector (current replica's perspective of other nodes latencies
        Long[] latency_vector = new Long[n];
        int myself = controller.getStaticConf().getProcessId();

        // Compute latencies to all other nodes
        for (int i = 0; i < n; i++) {

            // within monitoring interval [start = lastQuery; end = lastQuery + window]
            Map<Integer, Long> replicaRecvdTimes = recvdTimestamps.get(i).subMap(0, window); // Todo should not be necessary anymore?
            Map<Integer, Long> replicaSentTimes = sentTimestamps.get(i).subMap(0, window);

            ArrayList<Long> latencies = new ArrayList<>();
            for (Integer monitoringInstance : replicaRecvdTimes.keySet()) {
                Long rcvd = replicaRecvdTimes.get(monitoringInstance);
                Long sent = replicaSentTimes.get(monitoringInstance);
                if (rcvd != null) {
                    long latency = (rcvd - sent) / 2; // one-way latency as half of round trip time
                    // logger.info("Latency computed " + (double) Math.round((double) latency / 1000) / 1000.00 + " ms");
                    latencies.add(latency);
                }
            }
            latencies.sort(Comparator.naturalOrder());
            // If there are not latencies (e.g. a replica crashed) report with -1 (Failure value)
            Long medianValue = latencies.size() > 0 ? latencies.get(latencies.size() / 2) : Monitor.MISSING_VALUE;
            latency_vector[i] = medianValue;
            // logger.info("-- Size of " + replicaRecvdTimes.size());
        }
        // Assume self-latency is zero
        latency_vector[myself] = 0L;
        // printLatencyVector(latenciesToMillis(latency_vector));

        long end = System.nanoTime();
        logger.debug("Computed median latencies for " + description + "  in " + (double) (end - start) / 1000000.00 + " ms");
        return latency_vector;
    }

    /**
     * Clears timestamps
     */
    public synchronized void clear() {
        sentTimestamps.clear();
        recvdTimestamps.clear();
        if (sentMsgChallenges != null)
            sentMsgChallenges.clear();

        init();
    }

    public static double[] latenciesToMillis(Long[] m) {
        double[] latencies = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            double latency = Math.round((double) m[i] / 1000.00); // round to precision of micro seconds
            latencies[i] = latency / 1000.00; // convert to milliseconds
        }
        return latencies;
    }

    public static void printLatencyVector(double[] m) {
        String result = "\n";
        result += (".....................Measured latencies .......................\n");
        result += ("    0       1       2        3        4        ....    \n");
        result += ("...............................................................\n");
        for (double d : m) {
            if (d >= 0 && d < Monitor.MISSING_VALUE) {
                result = result + "  " + d + "  ";
            } else {
                result += "silent";
            }
        }
        result += "\n";
        result += ("...............................................................\n");
        result += "\n";
        final Logger logger = LoggerFactory.getLogger("bftsmart.aware.monitoring.MessageLatencyMonitor");
        logger.info(result);
    }

}
