package bftsmart.aware.tests;

import static bftsmart.aware.monitoring.MonitoringDataSynchronizer.bytesToLong;
import static bftsmart.aware.monitoring.MonitoringDataSynchronizer.longToBytes;


public class SerializationTest {

    /**
     * Tests some conversion methods
     */
    public static void main(String[] args) throws Exception {

        Long[] m = {0L, 20L, 100L, 200L, 200L};

        System.out.print("m (long) is ");
        for (Long l : m) {
            System.out.println(l);
        }

        byte[] toBytes = longToBytes(m);

        System.out.println("To Bytes " + toBytes);

        Long[] ident = bytesToLong(toBytes);

        System.out.println("Back to Long");
        for (Long l : ident) {
            System.out.println(l);
        }
    }
}
