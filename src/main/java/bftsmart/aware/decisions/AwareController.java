package bftsmart.aware.decisions;

import bftsmart.aware.monitoring.Monitor;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.ExecutionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implements adaptive wide-area replication
 *
 * Computes the best AWARE configuration used for a reconfiguration
 *
 * @author cb
 */
public class AwareController {

    // constants: emperically determined
    public static final int ROUNDS_AMORTIZATION = 10;
    public static final int N_SIZE_TO_USE_HEURISTICS = 10;

    private static AwareController instance;

    private AwareConfiguration currentDW;

    private WeightConfiguration current;

    // We will compute the worst, median and best weight configurations
    private WeightConfiguration worst; // for evaluations
    private WeightConfiguration median; // for evaluations
    private AwareConfiguration best;

    private ServerViewController viewControl;
    private final ExecutionManager executionManager;

    private Simulator simulator;

    public ServerViewController svc;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    /**
     * Singelton
     *
     * @param svc Server View Controller
     * @param executionManager Execution Manager
     * @return
     */
    public static AwareController getInstance(ServerViewController svc, ExecutionManager executionManager) {
        if (instance == null) {
            instance = new AwareController(svc, executionManager);
            WeightConfiguration current = new WeightConfiguration(svc.getStaticConf().isBFT(), svc);
            instance.setCurrent(current);
            instance.svc = svc;
        }
        return instance;
    }

    private AwareController(ServerViewController viewControl, ExecutionManager executionManager) {
        this.viewControl = viewControl;
        this.executionManager = executionManager;
        this.simulator = new Simulator(viewControl);

        // Debug
        // Periodically outputs current configuration
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // logger.info("[AwARE] Controller of id=" + svc.getStaticConf().getProcessId()
                //         + ": currently using weights " + instance.getCurrent()
                //         + ", leader " + executionManager.getCurrentLeader()
                //         + ", view " + svc.getCurrentView().getId()
                //         + ", last executed consensus " + executionManager.getLastExec()
                //         + ", current Delta " + viewControl.getCurrentView().getDelta());
            }
        }, 10*1000, 5*1000);
    }


    /**
     * This is where we start our search for the best weight configuration and protocol leader. We will generate
     * all possible weight configs first, then compute the predicted latency for all of them and for all possible
     * leader variants. Note, that this way, we traverse the entire search space. Since we compute combinations,
     * the search space is factorial in N and needs to be handled with some heuristics in larger systems
     */
    public AwareConfiguration computeBest() {
        Simulator simulator = new Simulator(viewControl);
        int[] replicaSet = viewControl.getCurrentViewProcesses();
        Monitor monitor = Monitor.getInstance(viewControl);

        int n = viewControl.getCurrentViewN();
        int f = viewControl.getCurrentViewF();
        int u = viewControl.getStaticConf().isBFT() ? 2 * f : f;
        int delta = viewControl.getStaticConf().getDelta();

        // init matrices
        long[][] propose = new long[n][n];
        long[][] write = new long[n][n];
        Long[][] propose_ast = monitor.sanitize(monitor.getM_propose());
        Long[][] write_ast = monitor.sanitize(monitor.getM_write());

        // Long to long
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                propose[i][j] = propose_ast[i][j];
                write[i][j] = write_ast[i][j];
            }
        }
        if (!instance.svc.getStaticConf().isUseDummyPropose()) {
            propose = write;
        }

        int cid = executionManager.getTOMLayer().getLastExec();

        currentDW = new AwareConfiguration(current, executionManager.getCurrentLeader());
        Long estimate_current = simulator.predictLatency(replicaSet, currentDW.getLeader(),
                currentDW.getWeightConfiguration(), propose, write, n, f, delta, ROUNDS_AMORTIZATION);
        currentDW.setPredictedLatency(estimate_current);

        // For larger systems, use heuristic, e.g, Simulated Annealing
        if (n > N_SIZE_TO_USE_HEURISTICS) {
            return Simulator.simulatedAnnealing(n, f, delta, u, replicaSet, propose, write, cid).best;
        }


        // Generate the search space:
        //      Computes all possible combinates of R_max and R_min distributions
        List<WeightConfiguration> weightConfigs = WeightConfiguration.allPossibleWeightConfigurations(u, replicaSet);

        List<AwareConfiguration> awareConfigurations = new ArrayList<>();
        int leader = executionManager.getCurrentLeader();

        // Generate the search space
        //      determine if leader should be selected or not
        for (WeightConfiguration w : weightConfigs) {
            if (viewControl.getStaticConf().isUseLeaderSelection()) {
                for (int primary : w.getR_max()) {
                    AwareConfiguration dwConfig = new AwareConfiguration(w, primary);
                    awareConfigurations.add(dwConfig);
                }
            } else {
                AwareConfiguration dwConfig = new AwareConfiguration(w, leader);
                awareConfigurations.add(dwConfig);
            }
        }

        // Compute the predictet latencies of all possible configurations using the simulator
        for (AwareConfiguration dwc : awareConfigurations) {
            Long predictedLatency = simulator.predictLatency(replicaSet, dwc.getLeader(), dwc.getWeightConfiguration(),
                    propose, write, n, f, delta, ROUNDS_AMORTIZATION);
            dwc.setPredictedLatency(predictedLatency);
            //logger.info("WeightConfig " + dwc.getWeightConfiguration() + "with leader " + dwc.getLeader() +
            //        " has predicted latency of " + ((double) Math.round(predictedLatency) / 1000) / 1000.00 + " ms");
        }

        // Sort configurations for ascending predicted latency
        awareConfigurations.sort(Comparator.naturalOrder());
        // We compare worst, median and best:
        AwareConfiguration best = awareConfigurations.get(0);
        AwareConfiguration median = awareConfigurations.get(awareConfigurations.size() / 2); // for evaluation
        AwareConfiguration worst = awareConfigurations.get(awareConfigurations.size() - 1); // for evaluation

        // For testing, remove later, make it debug later
        logger.info("the best config is " + best);
        logger.info("the median config is " + median);
        logger.info("the worst config is " + worst);
        logger.info("");
        logger.info("current config is estimated to be " + estimate_current);

        List<AwareConfiguration> bestConfigs = new ArrayList<>();
        for (AwareConfiguration dwc: awareConfigurations) {
            if (dwc.getPredictedLatency() == best.getPredictedLatency()) {
                bestConfigs.add(dwc);
            }
        }

        int currentLeader = this.executionManager.getCurrentLeader();

        best = bestConfigs.get(0);
        for (AwareConfiguration dwc: bestConfigs) {
            if (dwc.getLeader() == currentLeader) {
                best = dwc;
                break;
            }
        }

        this.best = best;
        return best;
    }


    /**
     * Optimizes weight distribution and leader selection
     *
     * @param cid consensus id
     */
    public void optimize(int cid) {
        // Re-calculate best weight distribution after every x consensus
        if (svc.getStaticConf().isUseDynamicWeights() && cid % svc.getStaticConf().getCalculationInterval() == 0 & cid > 0) {

            AwareController awareController = AwareController.getInstance(svc, executionManager);
            AwareConfiguration best = awareController.computeBest();
            AwareConfiguration current = awareController.getCurrentDW();

            // What is the best weight config and what is the current one?
            WeightConfiguration bestWeights = best.getWeightConfiguration();
            WeightConfiguration currentWeights = current.getWeightConfiguration();

            logger.info("");
            logger.info("!!! Best: " + best);
            logger.info("");

            if (svc.getStaticConf().isUseDynamicWeights()
                    && !currentWeights.equals(bestWeights)
                    && current.getPredictedLatency() > best.getPredictedLatency() * svc.getStaticConf().getOptimizationGoal()) {

                // The current weight configuration is not the best
                // Deterministically change weights (this decision will be the same in all correct replicas)
                // svc.getCurrentView().setWeights(bestWeights);
                View currentView = svc.getCurrentView();
                //     public View(int id, int[] processes, int f, InetSocketAddress[] addresses, boolean isBFT, int delta,
                View newView = new View(currentView.getId() + 1, currentView.getProcesses(), currentView.getF(),
                        currentView.getAddresses(), currentView.isBFT(), currentView.getDelta(), bestWeights);
                svc.reconfigureTo(newView);
                AwareController.getInstance(svc, executionManager).setCurrent(bestWeights);
                logger.info("|AWARE|-" + cid + "-[X] Optimization: Weight adjustment, now using " + bestWeights);
            } else {
                // Keep the current configuration
                logger.info("|AWARE|-"+ cid + "-[ ] Optimization: Weight adjustment, no adjustment," +
                        " current weight config is the best weight config");
            }

            if (svc.getStaticConf().isUseLeaderSelection()
                    && executionManager.getCurrentLeader() != best.getLeader()
                    && current.getPredictedLatency() > best.getPredictedLatency() * svc.getStaticConf().getOptimizationGoal()) {

                // The current leader is not the best, change it to the best
                int newLeader =  (best.getLeader()+svc.getCurrentViewN()) % svc.getCurrentViewN();
                executionManager.setNewLeader(newLeader);

                logger.info("-----_> NEW LEADER SET TO " + newLeader);
                if (svc.getStaticConf().getProcessId() == newLeader) {
                    executionManager.getTOMLayer().imAmTheLeader();
                    logger.info("-----_> I AM THE LEADER NOW" + newLeader);
                } else {
                    Acceptor acceptor = executionManager.getAcceptor();

                   // int cidNew = cid+1;
                    //logger.info("" + acceptor.proposeRecvd[newLeader]  + " " + cidNew );
                    //if(acceptor.proposeRecvd[newLeader] != null)
                       // logger.info("!!!! NUMBER" + acceptor.proposeRecvd[newLeader].getNumber());


                    if(acceptor.proposeRecvd[newLeader] != null && acceptor.proposeRecvd[newLeader].getNumber() == cid + 1) {
                        //logger.info("!!!!!!!!" + acceptor.proposeRecvd[newLeader] + " " + acceptor.proposeRecvd[newLeader].getNumber() + " " + cidNew );
                        //logger.info("!!!!!!!! Receiving propose of new leader");
                        acceptor.processMessage(acceptor.proposeRecvd[newLeader]);
                    }
                }

                logger.info("|AWARE|  [X] Optimization: leader selection, new leader is? " + best.getLeader());
            } else { // Keep the current configuration
                logger.info("|AWARE|  [ ] Optimization: leader selection: no leader change," +
                        " current leader is the best leader");
            }
            Monitor.getInstance(viewControl).init(svc.getCurrentViewN());
        }
    }


    /**
     * Getter and Setter
     **/

    public WeightConfiguration getCurrent() {
        return current;
    }

    public void setCurrent(WeightConfiguration current) {
        this.current = current;
    }

    public WeightConfiguration getWorst() {
        return worst;
    }

    public void setWorst(WeightConfiguration worst) {
        this.worst = worst;
    }

    public WeightConfiguration getMedian() {
        return median;
    }

    public void setMedian(WeightConfiguration median) {
        this.median = median;
    }

    public AwareConfiguration getBest() {
        return best;
    }

    public ServerViewController getViewControl() {
        return viewControl;
    }

    public void setViewControl(ServerViewController viewControl) {
        this.viewControl = viewControl;
    }

    public Simulator getSimulator() {
        return simulator;
    }

    public void setSimulator(Simulator simulator) {
        this.simulator = simulator;
    }

    public AwareConfiguration getCurrentDW() {
        return currentDW;
    }

    public void setCurrentDW(AwareConfiguration currentDW) {
        this.currentDW = currentDW;
    }
}
