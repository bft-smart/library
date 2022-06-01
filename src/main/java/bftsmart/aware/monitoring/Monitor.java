package bftsmart.aware.monitoring;

import bftsmart.consensus.Decision;
import bftsmart.consensus.Epoch;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.messages.TOMMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Singelton pattern. Only one instance of Monitor should be used
 *
 * @author cb
 */
public class Monitor {

    private static final int MONITORING_DELAY = 10 * 1000;
    private static final int MONITORING_PERIOD = 10 * 1000;

    public static final long MISSING_VALUE = 1000000000000000L; // Long does not have an infinity value, but this
    // value is very large for a latency, roughly
    // 10.000 seconds and will be used

    // Singelton
    private static Monitor instance;

    private ServerViewController svc;

    // Stores and computes latency monitoring information
    private MessageLatencyMonitor proposeLatencyMonitor;
    private MessageLatencyMonitor writeLatencyMonitor;

    // A timed synchronizer which will peridically disseminate monitoring, invokes them with total order
    private MonitoringDataSynchronizer monitoringDataSynchronizer;

    // The latencies the current process measures from its own perspective
    private Long[] freshestProposeLatencies;
    private Long[] freshestWriteLatencies;

    // The measured latency matrices which have been disseminated with total order
    // They are the same in all replicas for a defined consensus id, after all TOMMessages within this consensus
    // have been processed.
    private Long[][] m_propose;
    private Long[][] m_write;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    private Monitor(ServerViewController svc) {

        this.svc = svc;

        // Todo; if system size changes, we need to handle this
        int n = svc.getCurrentViewN();

        // Initialize
        this.writeLatencyMonitor = new MessageLatencyMonitor(svc);
        this.proposeLatencyMonitor = new MessageLatencyMonitor(svc);

        init(n);

        // Periodically compute point-to-pont latencies
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Computes the most recent point-to-point latency using the last 1000 (monitoring window) measurements
                // from consensus rounds
                freshestWriteLatencies = writeLatencyMonitor.create_L("WRITE");
                freshestProposeLatencies = proposeLatencyMonitor.create_L("PROPOSE");
            }
        }, MONITORING_DELAY, MONITORING_PERIOD);
    }

    /**
     * Use this method to get the monitor
     *
     * @param svc server view controller
     * @return the monitoring instance
     */
    public static Monitor getInstance(ServerViewController svc) {
        if (Monitor.instance == null) {
            Monitor.instance = new Monitor(svc);
        }
        return Monitor.instance;
    }

    public void startSync() {
        this.monitoringDataSynchronizer = new MonitoringDataSynchronizer(this.svc);
    }

    public MessageLatencyMonitor getWriteLatencyMonitor() {
        return writeLatencyMonitor;
    }

    public MessageLatencyMonitor getProposeLatencyMonitor() {
        return proposeLatencyMonitor;
    }

    public Long[] getFreshestProposeLatencies() {
        return proposeLatencyMonitor.create_L("PROPOSE");
    }

    public Long[] getFreshestWriteLatencies() {
        return writeLatencyMonitor.create_L("WRITE");
    }


    /**
     * Processes measurements decided in come consensus
     *
     * @param epoch contains the decision
     * @param cid consensus id
     */
    public void handleMonitoringMessages(Epoch epoch, int cid) {
        if (svc.getStaticConf().isUseDynamicWeights()) {
            for (TOMMessage tm : epoch.getConsensus().getDecision().getDeserializedValue()) {
                if (tm.getIsMonitoringMessage()) {
                    logger.debug("Received disseminated monitoring message ");
                    onReceiveMonitoringInformation(tm.getSender(), tm.getContent(), cid);
                }
            }
        }
    }

    public void handleMonitoringMessages(Decision decision) {
        if (svc.getStaticConf().isUseDynamicWeights()) {
            for (TOMMessage tm : decision.getDeserializedValue()) {
                if (tm.getIsMonitoringMessage()) {
                    logger.debug("Received disseminated monitoring message ");
                    onReceiveMonitoringInformation(tm.getSender(), tm.getContent(), decision.getConsensusId());
                }
            }
        }
    }

    /**
     * Gets called when a consensus completes and the consensus includes monitoring TOMMessages with measurement information
     * @param sender      a replica reporting its own measurement from its own perspective
     * @param value       a byte array containing the measurements
     * @param consensusID the specified consensus instance
     */
    private void onReceiveMonitoringInformation(int sender, byte[] value, int consensusID) {
        int n = svc.getCurrentViewN();

        Measurements li = Measurements.fromBytes(value);
        m_write[sender] = li.writeLatencies;
        m_propose[sender] = li.proposeLatencies;

        // Debugging and testing:
       // printM("PROPOSE", m_propose, consensusID, n);
        //printM("WRITE", m_write, consensusID, n);
    }


    /**
     * Assume communication link delays are symmetric and use the maximum
     *
     * @param m latency matrix
     * @return sanitized latency matrix
     */
    public Long[][] sanitize(Long[][] m) {
        int n = svc.getCurrentViewN();
        Long[][] m_ast = new Long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m_ast[i][j] = Math.max(m[i][j], m[j][i]);
            }
        }
        return m_ast;
    }

    private static void printM(String description, Long[][] matrix, int consensusID, int n) {
        String result = "";
        result += ("--------------- " + description + " ---------------------\n");
        result += ("Sever Latency Matrix for consensus ID " + consensusID + "\n");
        result += ("----------------------------------------------------------\n");
        result += ("       0       1       2        3        4        ....    \n");
        result += ("----------------------------------------------------------\n");
        for (int i = 0; i < n; i++) {
            result = result + i + " | ";
            for (int j = 0; j < n; j++) {

                double latency = Math.round((double) matrix[i][j] / 1000.00); // round to precision of micro seconds
                latency = latency / 1000.00; // convert to milliseconds
                if (latency >= 0.00 & latency < 1.0E9)
                    result += ("  " + latency + "  ");
                else
                    result += ("  silent  ");
            }
            result += "\n";
        }
        System.out.println(result);
    }

    public Long[][] getM_propose() {
        return m_propose;
    }

    public Long[][] getM_write() {
        return m_write;
    }

    public void init(int n) {
        this.m_propose = new Long[n][n];
        this.m_write = new Long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m_write[i][j] = MISSING_VALUE;
                m_propose[i][j] = MISSING_VALUE;
            }
        }
    }


}
