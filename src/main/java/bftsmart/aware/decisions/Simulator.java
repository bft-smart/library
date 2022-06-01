package bftsmart.aware.decisions;

import bftsmart.reconfiguration.ServerViewController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The class contains an algorithm to predict the latency of the BFT-SMaRt consensus algorithm based on
 * sever-to-server measurements and configurable voting weights (WHEAT). In particular, it simulates a protocol run
 * given the measured point-to-point latencies and computes the time each replica received a PROPOSE by the leader,
 * and the time at which each client forms a quorum in the WRITE phase. It then computes the time at which a client
 * quorum of replicas is ready to execute the request.
 *
 * @author cb
 */
public class Simulator {

    private ServerViewController viewControl;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public Simulator(ServerViewController controller) {
        this.viewControl = controller;
    }

    /**
     * Wrapper for PredictLatency using current view information and approximating PROPOSE latencies using WRITE latencies
     *
     * @param leader       selected leader for protocol simulation
     * @param weightConfig weight configuration to be simulated
     * @param m            sanitized WRITE latencies
     * @return predicted latency of the SMR protocol
     */
    public Long predictLatency(int leader, WeightConfiguration weightConfig, long[][] m) {

        // Use the PredictLatency Algorithm; M_PROPOSE = M_WRITE
        return predictLatency(leader, weightConfig, m, m);
    }

    /**
     * Wrapper for PredictLatency using current view information
     *
     * @param leader       selected leader for protocol simulation
     * @param weightConfig weight configuration to be simulated
     * @param m_propose    sanitized PROPOSE latencies
     * @param m_write      sanitized WRITE/ACCEPT latencies
     * @return predicted latency of the SMR protocol
     */
    public Long predictLatency(int leader, WeightConfiguration weightConfig, long[][] m_propose, long[][] m_write) {
        int[] replicaSet = viewControl.getCurrentViewProcesses();
        int n = viewControl.getStaticConf().getN();
        int f = viewControl.getStaticConf().getF();
        int delta = viewControl.getStaticConf().getDelta();

        // Use the PredictLatency Algorithm
        return this.predictLatency(replicaSet, leader, weightConfig, m_propose, m_write, n, f, delta, 10);
    }


    /**
     * Predics the latency of the SMR system for a given weight configuration and leader selection
     *
     * @param replicaSet   all replicas
     * @param leader       selected leader for protocol simulation
     * @param weightConfig weight configuration to be simulated
     * @param m_propose    sanitized PROPOSE latencies
     * @param m_write      sanitized WRITE/ACCEPT latencies
     * @param n            system size
     * @param f            number of faults
     * @param delta        number of additional spare replicas
     * @return predicted latency of the SMR protocol
     */
    public Long predictLatency(int[] replicaSet, int leader, WeightConfiguration weightConfig, long[][] m_propose,
                               long[][] m_write, int n, int f, int delta) {

        return this.predictLatency(replicaSet, leader, weightConfig, m_propose, m_write, n, f, delta, 1);
    }


    /**
     * Predics the latency of the SMR system for a given weight configuration and leader selection
     *
     * The runtime complexity is O(n²log(n)) for n number of replicas considering a constant number of rounds
     *
     * @param replicaSet   all replicas
     * @param leader       selected leader for protocol simulation
     * @param weightConfig weight configuration to be simulated
     * @param m_propose    sanitized PROPOSE latencies
     * @param m_write      sanitized WRITE/ACCEPT latencies
     * @param n            system size
     * @param f            number of faults
     * @param delta        number of additional spare replicas
     * @param rounds       number of consensus rounds used for calculation of amortized costs (calculation depth)
     * @return predicted latency of the SMR protocol
     */
    public Long predictLatency(int[] replicaSet, int leader, WeightConfiguration weightConfig, long[][] m_propose,
                               long[][] m_write, int n, int f, int delta, int rounds) {


        long[] consensusTimes = new long[rounds];
        long[] offsets = new long[n];
        boolean isBFT =  (viewControl == null) || viewControl.getStaticConf().isBFT();
        //int initialRoundNumber = rounds;
        //boolean offsetMatters = false;

        while (rounds > 0) { // If r > 1, compute the amortized consensus latency for multiple times r under the
            // assumptions that a new consensus starts immediately after the last instance finishes

            // Compute weights and quorum
            double V_min = 1.00;
            double V_max = V_min + (double) delta / (double) f;
            double[] V = new double[n];
            double Q_v = isBFT ? 2 * f * V_max + 1 : f * V_max + 1;

            // Assign binary voting weights to replicas
            for (int i : replicaSet)
                V[i] = weightConfig.getR_max().contains(i) ? V_max : V_min;

            // Simulate time every replica has been proposed to by the selected leader
            long[] t_proposed = new long[n];
            long[] t_write_finished = new long[n];
            long[] t_decided = new long[n];

            @SuppressWarnings("unchecked")
            PriorityQueue<Vote>[] writesRcvd = new PriorityQueue[n];

            @SuppressWarnings("unchecked")
            PriorityQueue<Vote>[] acceptRcvd = new PriorityQueue[n];


            // Compute time proposed time for all replicas. the proposed time is the maximum out of two times:
            //  (1) replica i has received the PROPOSE and (2) replica 1 has finished its last consensus
            //                                                 (respected by offsets that express waiting time)
            for (int i : replicaSet) {
              //  if (offsets[i] > m_propose[leader][i]) {
              //     offsetMatters = true;
              // }

                t_proposed[i] = Math.max(offsets[i], m_propose[leader][i]);
                writesRcvd[i] = new PriorityQueue<>();
                acceptRcvd[i] = new PriorityQueue<>();
               // if (rounds==1000)  System.out.println("Propose received " + i + " " + t_proposed[i]);
            }

            // Performance Optimization: If all offsets do not matter, return the consensus time of the first round
            //if (rounds < initialRoundNumber && !offsetMatters) return consensusTimes[rounds];

            // Compute time at which WRITE of replica j arrives at replica i
            for (int i : replicaSet) {
                for (int j : replicaSet) {
                    writesRcvd[i].add(new Vote(j, V[j], t_proposed[j] + m_write[j][i]));
                }
            }

            // Compute time at which replica i will finish its WRITE quorum
            for (int i : replicaSet) {
                double votes = 0.00;
                //Set<Integer> quorumUsed = new TreeSet<>();
                long t_written = Long.MAX_VALUE;
                while (votes < Q_v) {
                    Vote vote = writesRcvd[i].poll();
                    if (vote != null) {
                        votes += vote.weight;
                        t_written = vote.arrivalTime;
                        //quorumUsed.add(vote.castBy);
                    }
                }
                t_write_finished[i] = t_written;
               // if (rounds==1000) System.out.println("Written " + i + " " + t_written);
            }

            // Compute time at which ACCEPT of replica j arrives at replica i
            // CFT: we use proposed instead of write_finished because WRITE is skipped
            for (int i : replicaSet) {
                for (int j : replicaSet) {
                    acceptRcvd[i].add(new Vote(j, V[j], (isBFT ? t_write_finished[j] : t_proposed[i]) + m_write[j][i]));
                }
            }

            // Compute time at which replica i decides a value (finishes consensus)
            for (int i : replicaSet) {
                double votes = 0.00;
                while (votes < Q_v) {
                    Vote vote = acceptRcvd[i].poll();
                    if (vote != null) {
                        votes += vote.weight;
                        t_decided[i] = vote.arrivalTime;
                    }
                }
               // if (rounds==1000) System.out.println("Decided " + i + " " + t_decided[i]);

            }
            consensusTimes[rounds - 1] = t_decided[leader];

            // Compute offsets (time the other replicas need to finish their consensus round relative to the leader)
            for (int i = 0; i < n; i++)
                offsets[i] = t_decided[i] > t_decided[leader] ? t_decided[i] - t_decided[leader] : 0L;

            rounds--;
        }

        // Compute amortized consensus latency
        long sum = 0L;
        for (int i = 0; i < consensusTimes.length; i++) {
            sum += consensusTimes[i];
        }

        //DEBUG Delete later
        /*
        boolean printrounds = false;
        for (int i = 0; i < consensusTimes.length-1; i++) {
            for (int j = i; j < consensusTimes.length-1; j++) {
                if (consensusTimes[i] < consensusTimes[j]) {
                    //System.out.println("not monotonous ascending latency " + i + " " + j + " " + consensusTimes[i] + " " + consensusTimes[j]);
                    printrounds = true;
                }
            }
        }
        if (printrounds) {
            for (int i =  consensusTimes.length-1; i > 0; i--) {
                System.out.print(" " + consensusTimes[i]);
            }
        }


        if (!offsetMatters)
            System.out.print("OFFSETS do not matter");

        //END Delete later
        */

        //System.out.println("------------------------");


        return sum / consensusTimes.length;
    }



    public static SimulationRun simulatedAnnealing(int n, int f, int delta, int u, int[] replicaSet, long[][] propose, long[][] write, long seed) {

        long t1 = System.nanoTime();
        Simulator simulator = new Simulator(null);

        // Initialize
        WeightConfiguration w = new WeightConfiguration(u, replicaSet);
        AwareConfiguration x = new AwareConfiguration(w, 0);
        AwareConfiguration best = x;
        long prediction = simulator.predictLatency(replicaSet, x.getLeader(), x.getWeightConfiguration(), propose,
                write, n, f, delta, 10);
        best.setPredictedLatency(prediction);
        x.setPredictedLatency(prediction);
        Random random = new Random(seed);

        // Simulated Annealing parameters
        double temp = 25000;
        double coolingRate = 0.0055;
        double threshold = 0.5;


        // Debug Info
        double initTemp = temp;
        int examined = 0;
        int jumps = 0;
        int betterFound = 0;


        while (temp > threshold) {

            examined++;
            AwareConfiguration y = new AwareConfiguration(x.getWeightConfiguration().deepCopy(), x.getLeader());

            // Create a random variation of configuration x
            int randomReplicaFrom = random.nextInt(u);
            int randomReplicaTo = random.nextInt(n - u);

            TreeSet<Integer> R_max = (TreeSet<Integer>) y.getWeightConfiguration().getR_max();
            TreeSet<Integer> R_min = (TreeSet<Integer>) y.getWeightConfiguration().getR_min();

            Integer max = (Integer) R_max.toArray()[randomReplicaFrom];
            Integer min = (Integer) R_min.toArray()[randomReplicaTo];

            // Get energy of solutions
            Long predictX = x.getPredictedLatency();


            // Swap min and max replica
            if (max.equals(y.getLeader())) {
                y.setLeader(min);
            }
            R_max.remove(max);
            R_max.add(min);

            R_min.remove(min);
            R_min.add(max);

            Long predictY = simulator.predictLatency(replicaSet, y.getLeader(), y.getWeightConfiguration(), propose, write, n, f, delta, 10);

            // If the new solution is better, it is accepted
            if (predictY < predictX) {
                x = y;
                x.setPredictedLatency(predictY);
                betterFound++;

            } else {
                // If the new solution is worse, calculate an acceptance probability
                double rand = random.nextDouble();
                if (Math.exp(-((predictY - predictX) / (temp))) > rand) {
                    jumps++;
                    x = y;
                    x.setPredictedLatency(predictY);
                }
            }

            // Record best solution found
            if (predictY < best.getPredictedLatency()) {
                best = y;
                best.setPredictedLatency(predictY);
            }

            // Cool system down
            temp *= 1 - coolingRate;
        }


        long t2= System.nanoTime();
        double time = ((double) (t2 - t1)) / 1000000.00; // in ms

        String additionalParameters = "temperature: " + initTemp + " coolingRate: " + coolingRate + " threshold: "
                + threshold + " jumps: " + jumps + " better found: " + betterFound;

        Simulator.printStrategyInfo("Simulated Annealing", examined, best, time, additionalParameters);

        return simulator.new SimulationRun(time, best.getPredictedLatency(), best);
    }


    public static SimulationRun tabuSearch(int n, int f, int delta, int u, int[] replicaSet, long[][] propose, long[][] write, int examined, long seed) {

        long t1 = System.nanoTime();

        Simulator simulator = new Simulator(null);
        Random random = new Random(seed);

        // Initialize
        AwareConfiguration x = new AwareConfiguration(new WeightConfiguration(u, replicaSet), 0);
        AwareConfiguration best = x;
        long prediction = simulator.predictLatency(replicaSet, x.getLeader(), x.getWeightConfiguration(), propose,
                write, n, f, delta, 10);
        best.setPredictedLatency(prediction);
        x.setPredictedLatency(prediction);
        int initExamined = examined;

        int maxTabuSize = 10;

        Stack<AwareConfiguration> tabuList = new Stack<>();
        tabuList.push(x);

        while (examined > 0) {

            List<AwareConfiguration> neighbors = x.getNeighborhood(random.nextInt(n - u));

            for (AwareConfiguration ac : neighbors) {
               // System.out.println(ac.getWeightConfiguration() + " L" + ac.getLeader());
                long fitnessY = simulator.predictLatency(replicaSet, ac.getLeader(), ac.getWeightConfiguration(), propose,
                        write, n, f, delta, 10);

                if (!tabuList.contains(ac) && fitnessY < best.getPredictedLatency()) {
                    x = ac;
                    x.setPredictedLatency(fitnessY);
                }
            }
            if (x.getPredictedLatency() < best.getPredictedLatency()) {
                best = x;
            }
            tabuList.push(x);
            if (tabuList.size() > maxTabuSize) {
                tabuList.remove(0);
            }

            examined = examined - neighbors.size();
        }

        long t2 = System.nanoTime();
        double time = ((double) (t2 - t1)) / 1000000.00; // in ms
        String additionalParams = "maxTabuSize: " + maxTabuSize;
        printStrategyInfo("Tabu Search", initExamined, best, time, additionalParams);

        return simulator.new SimulationRun(time, best.getPredictedLatency(), best);


    }


    public static SimulationRun pickSampleConfigs(int n, int f, int delta, int u, int[] replicaSet, long[][] propose, long[][] write, int sample) {

        long t1 = System.nanoTime();
        List<WeightConfiguration> weightConfigs = WeightConfiguration.allPossibleWeightConfigurations(u, replicaSet);
        Simulator simulator = new Simulator(null);

        long bestLatency = Long.MAX_VALUE;
        long worstLatency = Long.MIN_VALUE;

        AwareConfiguration best = new AwareConfiguration(new WeightConfiguration(u, replicaSet), 0);
        AwareConfiguration worst = new AwareConfiguration(new WeightConfiguration(u, replicaSet), 0);

        int skip = Math.max(weightConfigs.size()*u/sample,1);
        int count = 0;
        int examined = 0;
        for (WeightConfiguration w : weightConfigs) {
            count++;
            if (sample == -1 || count % skip == 0) {
                for (int primary : w.getR_max()) { // Only replicas in R_max will be considered to become leader ?
                    examined++;
                    Long prediction = simulator.predictLatency(replicaSet, primary, w, propose, write, n, f, delta, 10);

                    if (prediction < bestLatency) {
                        bestLatency = prediction;
                        best = new AwareConfiguration(w, primary, prediction);
                    }

                    if (prediction > worstLatency) {
                        worstLatency = prediction;
                        worst = new AwareConfiguration(w, primary, prediction);
                    }
                }
            }

        }
        long t2 = System.nanoTime();
        double time = ((double) (t2 - t1)) / 1000000.00; // in ms

        String additionalParameters = sample == -1 ? "Worst Configuration performs in " + worstLatency + " ms" : "";

        String strategy = sample != -1 ? "Wide-Spread Sample of " + sample : "Exhaustive Search (Brute-Force)";

        Simulator.printStrategyInfo(strategy, examined, best, time, additionalParameters);

        return sample != -1 ? simulator.new SimulationRun(time, best.getPredictedLatency(), best):
                simulator.new SimulationRun(time, best.getPredictedLatency(), best, worst);
    }




    public static SimulationRun exhaustiveSearch(int n, int f, int delta, int u, int[] replicaSet, long[][] propose, long[][] write) {

        return pickSampleConfigs(n, f, delta, u, replicaSet, propose, write, -1);

    }


    public static void printStrategyInfo(String strategyName, int examined, AwareConfiguration best, double timeNeeded,
                                         String additionalParameters) {

        System.out.println();
        System.out.println("----------------------------------------------------");
        System.out.println("------------------- " + strategyName);
        System.out.println("----------------------------------------------------");
        System.out.println("Configurations examined: " + examined + "      time needed: " + timeNeeded + " ms" );
        System.out.println("Final solution latency: " + best.getPredictedLatency());
        System.out.println("Best Configuratuon: " + " " + best.getWeightConfiguration() + " with leader " + best.getLeader());
        System.out.println(additionalParameters);
        System.out.println();

    }

        public class Vote implements Comparable {

        int castBy; // replicaID which has cast the vote
        double weight; // weight of the vote i.e. V_min or V_max
        long arrivalTime; // timestamp of simulated arrival of the vote

        Vote(int castBy, double weight, long arrivalTime) {
            this.castBy = castBy;
            this.weight = weight;
            this.arrivalTime = arrivalTime;
        }

        @Override
        public int compareTo(Object o) {
            return Long.compare(this.arrivalTime, ((Vote) o).arrivalTime);
        }
    }

    public class SimulationRun implements Comparable {

        double timeNeeded;
        long solutionLatency;
        AwareConfiguration best;
        AwareConfiguration worst;

        SimulationRun(double time, long solutionLatency, AwareConfiguration best, AwareConfiguration worst) {
            this.timeNeeded = time;
            this.solutionLatency = solutionLatency;
            this.best = best;
            this.worst = worst;
        }

        SimulationRun(double time, long solutionLatency, AwareConfiguration best) {
            this.timeNeeded = time;
            this.solutionLatency = solutionLatency;
            this.best = best;
        }

        public long getSolutionLatency() {
            return solutionLatency;
        }

        public double getTimeNeeded() {
            return timeNeeded;
        }

        public long getWorstLatency() {
            return worst.getPredictedLatency();
        }

        @Override
        public int compareTo(Object o) {
            return Long.compare(this.solutionLatency, ((SimulationRun) o).solutionLatency);
        }
    }

}
