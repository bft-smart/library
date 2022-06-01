package bftsmart.aware.tests;

import bftsmart.aware.decisions.Simulator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


/**
 * Tests finding the best weight configuration and the best leader
 *
 * @author cb
 */
public class FindOptimalWeightConfigTest {

    /**
     * Tests if simulator works.
     *
     * @param args the command line arguments
     * @author cb
     */
    public static void main(String[] args) throws Exception {


        int[][] f_Delta = {
                {2, 1}, {2, 2}, {2, 3}, {3, 1}, {3, 2}, {3, 3}, {4, 1}, {4, 2}, {4, 3}, {4, 4}
        };
        int runs = 1000;



        for (int k = 0; k < f_Delta.length; k++) {

            Simulator simulator = new Simulator(null);


            int f = f_Delta[k][0];
            int delta = f_Delta[k][1];

            int n = 3*f+1+delta;

            int u = 2 * f;
            int[] replicaSet = new int[n];
            for (int i = 0; i < n; i++) {
                replicaSet[i] = i;
            }

            String lines = "";

            Simulator.SimulationRun[] resultsPick = new Simulator.SimulationRun[runs];
            Simulator.SimulationRun[] resultstabu = new Simulator.SimulationRun[runs];
            Simulator.SimulationRun[] resultsAnnealing = new Simulator.SimulationRun[runs];
            Simulator.SimulationRun[] resultsExhaustive = new Simulator.SimulationRun[runs];


            for (int i = 0; i < runs; i++) {
                System.out.println("Find the optimum... " + i + " of 1000");


        /*
        long[][] propose = {
                {0,	67739,	69185,	93000,	40285},
                {67739,	0,	132581,	92021,	35496},
                {69185,	132581,	0,	156703,	99237},
                {93000,	92021,	156703,	0,	70210},
                {40285,	35496,	99237,	70210,	0}

        };*/

                // Sydney, Stockholm, California, Tokio, Sao Paulo
                long[][] propose = generateTestM(n);
                /*{
                {0,	318840,	148980,	113120,	316410},
                {318840,	0,	173160,	262680,	233120},
                {148980,	173160,	0,	115960,	195920},
                {113120,	262280,	115960,	0,	288740},
                {316410,	233120,	195920,	288740,	0}

        };*/


                long[][] write = propose;

                // Start the calculations

                Simulator.SimulationRun pickSample = Simulator.pickSampleConfigs(n, f, delta, u, replicaSet, propose, write, 1160);
                resultsPick[i] = pickSample;

                Simulator.SimulationRun tabuSearch = Simulator.tabuSearch(n, f, delta, u, replicaSet, propose, write, 1160, 500);
                resultstabu[i] = tabuSearch;

                Simulator.SimulationRun simulatedAnnealing = Simulator.simulatedAnnealing(n, f, delta, u, replicaSet, propose, write, 500);
                resultsAnnealing[i] = simulatedAnnealing;

                Simulator.SimulationRun exhaustiveSearch = Simulator.exhaustiveSearch(n, f, delta, u, replicaSet, propose, write);
                resultsExhaustive[i] = exhaustiveSearch;


                lines += pickSample.getSolutionLatency() + ", " + tabuSearch.getSolutionLatency() + ", " +
                        simulatedAnnealing.getSolutionLatency() + ", " + exhaustiveSearch.getSolutionLatency() + ", " + exhaustiveSearch.getWorstLatency() + ", ";

                lines += pickSample.getTimeNeeded() + ", " + tabuSearch.getTimeNeeded() + ", " +
                        simulatedAnnealing.getTimeNeeded() + ", " + exhaustiveSearch.getTimeNeeded() + "\n";

            }

            System.out.println("Calculate Results...");
            double[] errorsAnnealing = new double[runs];
            double[] errorsPick = new double[runs];

            double sumAnnealing = 0.0;
            double sumPick = 0.0;

            int exactSolutionsAnnealing = 0;
            int exactSolutionsPick = 0;

            double timeAnnealing = 0.0;
            double timeExhaustive = 0.0;
            double timePick = 0.0;

            for (int i = 0; i < runs; i++) {
                errorsAnnealing[i] = (double) resultsAnnealing[i].getSolutionLatency() / (double) resultsExhaustive[i].getSolutionLatency();
                errorsPick[i] = (double) resultsPick[i].getSolutionLatency() / (double) resultsExhaustive[i].getSolutionLatency();


                if (resultsAnnealing[i].getSolutionLatency() == resultsExhaustive[i].getSolutionLatency())
                    exactSolutionsAnnealing++;

                if (resultsPick[i].getSolutionLatency() == resultsExhaustive[i].getSolutionLatency())
                    exactSolutionsPick++;

                sumAnnealing += errorsAnnealing[i];
                sumPick += errorsPick[i];

                timeAnnealing += resultsAnnealing[i].getTimeNeeded();
                timeExhaustive += resultsExhaustive[i].getTimeNeeded();
                timePick += resultsPick[i].getTimeNeeded();
            }
            double avgErrorAnnealing = sumAnnealing / (double) runs;
            double avgErrorPick = sumPick / (double) runs;

            double exactSolutionsPercentAnnealing = (double) exactSolutionsAnnealing / (double) runs;
            double exactSolutionsPercentPick = (double) exactSolutionsPick / (double) runs;

            double avgTimeAnnealing = timeAnnealing / (double) runs;
            double avgTimeExhaustive = timeExhaustive / (double) runs;
            double avgTimePick = timePick / (double) runs;

            String results = ""+ n + ", " + f + ", " + delta + ", "; //"avg Error (Annealing), Exact Solutions Annealing (%), avg Time (Annealing), avg Error (PickSample), Exact Solutions Pick (%), avg Time (PickSample), avg Time (Exhaustive) \n";
            results = results + avgErrorAnnealing + ", " + exactSolutionsPercentAnnealing + ", " + avgTimeAnnealing + ", " + avgErrorPick + ", " + exactSolutionsPercentPick + ", " + avgTimePick + ", " + avgTimeExhaustive + "\n";

            Writer output;
            try {
                output = new BufferedWriter(new FileWriter("simulation " + f_Delta[k][0] + "_" + f_Delta[k][1], false));
                output.append(lines);
                output.close();

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("!!!!!!!!!!!!!!! Something went wrong " + e.getStackTrace());
            }

            try {
                output = new BufferedWriter(new FileWriter("results", true));
                output.append(results);
                output.close();

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("!!!!!!!!!!!!!!! Something went wrong " + e.getStackTrace());
            }

        }
    }


    private static long[][] generateTestM(int n) {

        long[][] M = new long[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                long random = (long) (Math.random() * 100);
                M[i][j] = i == j ? 0 : random;
                M[j][i] = i == j ? 0 : random;
            }
        }
        return M;
    }
}