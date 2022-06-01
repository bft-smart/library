package bftsmart.aware.decisions;

import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.views.View;

import java.util.*;

import java.util.Map;

/**
 * This class manages a weight configuration.
 * We call the partitioning of a WHEAT replica set I in R_max and R_min a weight configuration W = {R_max, R_min}
 * if R_max contains 2f (BFT) or f replicas (CFT)
 * This class also contains a helper function to generate all possible weight configurations
 * (combinations of drawing R_max from replica set
 *
 * @author cb
 */
public class WeightConfiguration {

    private Set<Integer> R_max;
    private Set<Integer> R_min;

    private long predictedLatency;

    private List<List<Integer>> rmaxCombinations = new ArrayList<>();

    /**
     * Creates a weight configuration from the current view
     */
    public WeightConfiguration(boolean isBFT, ServerViewController controller) {

        int n = controller.getCurrentViewN();
        int f = controller.getCurrentViewF();

        R_max = new TreeSet<Integer>();
        R_min = new TreeSet<Integer>();

        View view = controller.getCurrentView();

        Map<Integer, Double> weights = view.getWeights();

        int count = n;
        for (int i = n-1; i >= 0; i--) {
            if (weights.get(i) == 1.00 && count > 2*f) {
                R_min.add(i);
                count--;
            } else {
                R_max.add(i); // in a system with delta = 0, all replicas have 1.0 votes, even the ones in R_max
            }
        }
    }

    /**
     * Creates new Weight Config from replica array (permutation) assuming the V_max Replicas are listed first
     *
     * @param u          nmuber of Vmax replicass: 2f (BFT) or f (CFT)
     * @param replicaSet all replicas
     */
    public WeightConfiguration(int u, int[] replicaSet) {

        R_max = new TreeSet<Integer>();
        R_min = new TreeSet<Integer>();

        for (int i = 0; i < u; i++) {
            R_max.add(replicaSet[i]);
        }
        for (int i = u; i < replicaSet.length; i++) {
            R_min.add(replicaSet[i]);
        }

    }


    /**
     * Creates new Weight Config from replica array (permutation) assuming the V_max Replicas are listed first
     *
     * @param r_max nmuber of Vmax replicass: 2f (BFT) or f (CFT)
     * @param r_min all replicas
     */
    public WeightConfiguration(Set<Integer> r_max, Set<Integer> r_min) {

        this.R_max = r_max;
        this.R_min = r_min;

    }


    /**
     * Creates all possible WeightConfigurations from given a replica set and the u param (number of Vmax replicas)
     *
     * @param u          nmuber of Vmax replicass: 2f (BFT) or f (CFT)
     * @param replicaSet all replicas
     */
    public static List<WeightConfiguration> allPossibleWeightConfigurations(int u, int[] replicaSet) {

        int data[] = new int[u];
        WeightConfiguration w = new WeightConfiguration(u, replicaSet);

        // Recursively create all possible binominal combinations of "drawing u out of n"
        w.combinationUtil(replicaSet, data, 0, replicaSet.length - 1, 0, u);

        // Get all weight configurations as int array
        List<List<Integer>> combinations = w.getRmaxCombinations();
        List<WeightConfiguration> weightConfigs = new ArrayList<>();

        // Build the R_max and R_min sets for each combination array
        for (List<Integer> combination : combinations) {

            Set<Integer> r_max = new TreeSet<>(combination);
            Set<Integer> r_min = new TreeSet<>();

            for (int i = 0; i < replicaSet.length; i++) {
                if (!r_max.contains(i)) {
                    r_min.add(i);
                }
            }

            // Create and add weight config to list
            WeightConfiguration weightConfig = new WeightConfiguration(r_max, r_min);
            weightConfigs.add(weightConfig);
        }

        return weightConfigs;
    }

    /**
     * Wrapper
     * Creates all possible WeightConfigurations by computing all combinations based on this weight configuration
     */
    public List<WeightConfiguration> allPossibleWeightConfigurations() {

        int u = R_max.size();
        int[] replicaSet = this.getReplicaSet();

        return WeightConfiguration.allPossibleWeightConfigurations(u, replicaSet);
    }


    private void combinationUtil(int arr[], int data[], int start, int end, int index, int r) {
        // Current combination is ready to be printed, print it
        if (index == r) {
            List<Integer> combination = new ArrayList<>();
            for (int j = 0; j < r; j++) {
                combination.add(data[j]);
            }
            this.rmaxCombinations.add(combination);
            return;
        }
        // replace index with all possible elements. The condition
        // "end-i+1 >= r-index" makes sure that including one element
        // at index will make a combination with remaining elements
        // at remaining positions
        for (int i = start; i <= end && end - i + 1 >= r - index; i++) {
            data[index] = arr[i];
            combinationUtil(arr, data, i + 1, end, index + 1, r);
        }
    }


    public Set<Integer> getR_max() {
        return R_max;
    }

    public Set<Integer> getR_min() {
        return R_min;
    }

    public long getPredictedLatency() {
        return predictedLatency;
    }

    public void setPredictedLatency(long predictedLatency) {
        this.predictedLatency = predictedLatency;
    }

    public int[] getReplicaSet() {
        int[] replicaSet = new int[R_max.size() + R_min.size()];
        int i = 0;
        for (Integer replica : R_max) {
            replicaSet[i] = replica;
            i++;
        }
        for (Integer replica : R_min) {
            replicaSet[i] = replica;
            i++;
        }
        return replicaSet;
    }

    public List<List<Integer>> getRmaxCombinations() {
        return rmaxCombinations;
    }

    @Override
    public String toString() {
        String result = "R_max: ";
        for (int i : R_max)
            result += i + " ";

        result += " | R_min: ";

        for (int i : R_min)
            result += i + " ";

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeightConfiguration that = (WeightConfiguration) o;
        return this.R_max.containsAll(that.R_max) && this.R_min.containsAll(that.R_min) &&
                that.R_max.containsAll(this.R_max) && that.R_min.containsAll(this.R_min);
    }


    @Override
    public int hashCode() {
        return Objects.hash(R_max, R_min);
    }


    public WeightConfiguration deepCopy() {

        TreeSet<Integer> r_max = (TreeSet<Integer>) (((TreeSet<Integer>) this.getR_max()).clone());
        TreeSet<Integer> r_min = (TreeSet<Integer>) (((TreeSet<Integer>) this.getR_min()).clone());

        return new WeightConfiguration(r_max, r_min);
    }
}
