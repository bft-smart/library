/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.demo.counter;

/**
 *
 * @author alysson
 */
public class MultiCounterClient {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        int numOfClients = (args.length > 0)?Integer.parseInt(args[0]):2;
        int initialProcess = (args.length > 1)?Integer.parseInt(args[1]):4;

        Process[] p = new Process[numOfClients];
        
        for (int i = 0; i < numOfClients; i++) {
            int id = initialProcess+i;
            int inc = 1;

            System.out.println("Starting client "+id);

            //UNIX (not tested yet!)
            p[i] = Runtime.getRuntime().exec("/bin/sh -e java -cp dist/SMART-SVN.jar "
                    + "navigators.smart.tom.demo.counter.CounterClient " + id + " " + inc + " 5000"
                    + " > output-" + id + "-" + inc + ".txt 2>&1");

            //Windows
            //p[i] = Runtime.getRuntime().exec("cmd /c java -cp dist/SMaRt.jar "
            //        + "navigators.smart.tom.demo.counter.CounterClient " + id + " " + inc
            //        + " > output-" + id + "-" + inc + ".txt 2>&1");
        }

        for (int i = 0; i < numOfClients; i++) {
            int r = p[i].waitFor();
            System.out.println("Client "+(i+initialProcess)+" finished with "+r);
        }

    }
}
