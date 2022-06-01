package bftsmart.aware.monitoring;

import java.io.*;

/**
 * Measurements that represent latency vectors Li = <l0, l1, .. ln-1> that replicas will invoke with total order
 * then use for optimizations...
 */
public class Measurements {

    public int n; // number of replicas
    public Long[] writeLatencies;
    public Long[] proposeLatencies;

    public Measurements() { }

    public Measurements(int n, Long[] writeLatencies, Long[] proposeLatencies) {
        this.n = n;
        this.writeLatencies = writeLatencies;
        if (proposeLatencies != null) {
            this.proposeLatencies = proposeLatencies;
        } else {
            this.proposeLatencies = new Long[n];
            for (int i = 0; i < n; i++)
                this.proposeLatencies[i] = Monitor.MISSING_VALUE;
        }
    }

    public  byte[] toBytes() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(n);
            for (Long l : writeLatencies)
                dos.writeLong(l);

            for (Long l : proposeLatencies) {
                if (l != null)
                    dos.writeLong(l);
            }

            dos.close();
        } catch (IOException e) {
            System.out.println("!!!!!!!!!!!!!!! Something went wrong " + e.getStackTrace());
        }

        return baos.toByteArray();
    }

    public static Measurements fromBytes(byte[] measurements) {
        int n = 0;
        Long[] writeLatencies = new Long[0];
        Long[] proposeLatencies = new Long[0];

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(measurements);
            DataInputStream dis = new DataInputStream(bis);
            n = dis.readInt();
            writeLatencies = new Long[n];
            proposeLatencies = new Long[n];

            for (int i = 0; i < n; i++)
                writeLatencies[i] = dis.readLong();

            for (int i = 0; i < n; i++)
                proposeLatencies[i] = dis.readLong();

            dis.close();
        } catch (IOException e) {
            System.out.println("!!!!!!!!!!!!!!! Something went wrong " + e.getStackTrace());
        }

        return new Measurements(n, writeLatencies, proposeLatencies);
    }


}