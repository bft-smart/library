package bftsmart.aware.decisions;

import bftsmart.aware.monitoring.Monitor;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * AWARE Configuration includes a weight configuration and a leader selection
 *
 * @author cb
 */
public class AwareConfiguration implements Comparable<AwareConfiguration> {

    // A AWARE config consists of a weight distribution and leader selection
    private WeightConfiguration weightConfiguration;
    private int leader;

    // predicted latency for this configuration
    private long predictedLatency = Monitor.MISSING_VALUE;

    public AwareConfiguration(WeightConfiguration weightConfiguration, int leader, long predictedLatency) {
        this.weightConfiguration = weightConfiguration;
        this.leader = leader;
        this.predictedLatency = predictedLatency;
    }

    public AwareConfiguration(WeightConfiguration weightConfiguration, int leader) {
        this.weightConfiguration = weightConfiguration;
        this.leader = leader;
    }

    @Override
    public int compareTo(AwareConfiguration o) {
        return Long.compare(this.predictedLatency, o.predictedLatency);
    }

    public WeightConfiguration getWeightConfiguration() {
        return weightConfiguration;
    }

    public void setWeightConfiguration(WeightConfiguration weightConfiguration) {
        this.weightConfiguration = weightConfiguration;
    }

    public int getLeader() {
        return leader;
    }

    public void setLeader(int leader) {
        this.leader = leader;
    }

    public long getPredictedLatency() {
        return predictedLatency;
    }

    public void setPredictedLatency(long predictedLatency) {
        this.predictedLatency = predictedLatency;
    }

    @Override
    public String toString() {
        String result = weightConfiguration.toString() + " with leader " + leader + " ";
        if (predictedLatency != Monitor.MISSING_VALUE)
            result += "and latency of " + predictedLatency;

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AwareConfiguration that = (AwareConfiguration) o;
        return leader == that.leader &&
                weightConfiguration.equals(that.weightConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weightConfiguration, leader);
    }


    /**
     * Creates a list of neighbor configurations given some specific configuration
     *
     * @param randomReplicaTo a specific configuration for which a neighborhood should be created
     * @return list of configuration with 1-distance
     */
    public List<AwareConfiguration> getNeighborhood(int randomReplicaTo) {

        List<AwareConfiguration> neighbors = new LinkedList<>();

        for (Integer max : this.getWeightConfiguration().getR_max()) {


            AwareConfiguration y = new AwareConfiguration(this.getWeightConfiguration().deepCopy(), this.getLeader());

            TreeSet<Integer> R_max = (TreeSet<Integer>) y.getWeightConfiguration().getR_max();
            TreeSet<Integer> R_min = (TreeSet<Integer>) y.getWeightConfiguration().getR_min();

            Integer min = (Integer) R_min.toArray()[randomReplicaTo];

            // Swap min and max replica
            if (max.equals(y.getLeader()))
                y.setLeader(min);

            R_max.remove(max);
            R_max.add(min);

            R_min.remove(min);
            R_min.add(max);

            neighbors.add(y);
        }

        return neighbors;

    }
}
