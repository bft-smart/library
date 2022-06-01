package bftsmart.aware.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import bftsmart.aware.decisions.Simulator;
import bftsmart.aware.decisions.Simulator.SimulationRun;

public class TestAWSLatencies {
    public static void main(String[] args) {

        int SIZE = 21; // number of replicas
        int INCREMENT = 1; // increment applied to delta in each iteration
        String strategy = "";  // "SA" or "Exhaustive"
        if (args.length > 0) {
            strategy = args[0];
        }


        try {
            long[][] m = readMatrix("./data/cloudPing/cloudping.csv", SIZE, SIZE, ","); // input
            int replicaset[] = makeReplicaSet(SIZE);

            int delta = 0;
            int f = (SIZE - 1 - delta)/3;
            int u = 2*f;

            BufferedWriter w = new BufferedWriter(new FileWriter("./data/output_test_" + strategy + ".csv")); // output
            w.write("delta, faults, latency\n");

            while(f > 0) {
                System.out.println("_________________________________________________________________________");
                System.out.println("Delta: " + delta);
                System.out.println("f: " + f);

                SimulationRun sim;

                if (strategy.equals("SA")) {
                    sim = Simulator.simulatedAnnealing(SIZE, f, delta, u, replicaset, m, m, 0);
                } else {
                    sim = Simulator.exhaustiveSearch(SIZE, f, delta, u, replicaset, m, m);
                }

                w.write(delta + "," + f + "," + sim.getSolutionLatency() + "\n");

                delta += INCREMENT;
                f = (SIZE - 1 - delta)/3;
                u = 2*f;
            }

            w.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

//		int r_size = 200;
//		int r_delta = 0;
//		int r_f = (r_size - 1 - r_delta)/3;
//		int r_u = 2*r_f;
//		int r_replicaset[] = makeReplicaSet(r_size);
//		long r_m[][] = randomMatrix(r_size);
//
//		Simulator.simulatedAnnealing(r_size, r_f, r_delta, r_u, r_replicaset, r_m, r_m, 0);
    }

    private static int[] makeReplicaSet(int n) {
        int ls[] = new int[n];
        for (int i = 0; i < n; i++)
            ls[i] = i;
        return ls;
    }

    private static void printmatrix(long[][] m) {
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                System.out.print(m[i][j] + " ");
            }
            System.out.println();
        }
    }

    public static long[][] randomMatrix(int size) {
        Random r = new Random();
        long[][] m = new long[size][size];
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                m[i][j] = r.nextInt(200);
            }
        }
        return m;
    }

    /**
     *
     * @param filename
     * @param n - lines
     * @param m - columns
     * @return
     * @throws IOException
     */
    public static long[][] readMatrix(String filename, int n, int m, String div) throws IOException {

        BufferedReader reader = new BufferedReader(new FileReader(filename));

        long matrix[][] = new long[n][m];

        String line[];

        for (int i = 0; i < n; i++) {
            line = reader.readLine().split(div);
            for (int j = 0; j < m; j++) {
                matrix[i][j] = Math.round(Double.parseDouble(line[j])*100);// multiplication to keep two decimals
            }
        }

        reader.close();

        return matrix;
    }
}