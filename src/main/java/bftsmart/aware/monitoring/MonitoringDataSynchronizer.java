package bftsmart.aware.monitoring;

import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.ServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class disseminates this replicas measurements with total order
 *
 * @author cb
 */
public class MonitoringDataSynchronizer {

    private ServiceProxy monitoringDataDisseminationProxy;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    /**
     * Creates a new Synchronizer to disseminate data with total order
     *
     * @param svc server view controller
     */
    MonitoringDataSynchronizer(ServerViewController svc) {

        int myID = svc.getStaticConf().getProcessId();
        monitoringDataDisseminationProxy = new ServiceProxy(myID);

        // Create a time to periodically broadcast this replica's measurements to all replicas
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                // Get freshest write latenciesfrom Monitor
                Long[] writeLatencies = Monitor.getInstance(svc).getFreshestWriteLatencies();
                Long[] proposeLatencies = Monitor.getInstance(svc).getFreshestProposeLatencies();

                Measurements li = new Measurements(svc.getCurrentViewN(), writeLatencies, proposeLatencies);
                byte[] data = li.toBytes();

                monitoringDataDisseminationProxy.invokeOrderedMonitoring(data);

                logger.debug("|---> Disseminating monitoring information with total order! ");
            }
        }, svc.getStaticConf().getSynchronisationDelay(), svc.getStaticConf().getSynchronisationPeriod());
    }

    /**
     * Converts Long array to byte array
     *
     * @param array Long array
     * @return byte array
     * @throws IOException
     */
    public static byte[] longToBytes(Long[] array) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (Long l : array)
            dos.writeLong(l);

        dos.close();
        return baos.toByteArray();
    }

    /**
     * Converts byte array to Long array
     *
     * @param array byte array
     * @return Long array
     * @throws IOException
     */
    public static Long[] bytesToLong(byte[] array) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(array);
        DataInputStream dis = new DataInputStream(bais);
        int n = array.length / Long.BYTES;
        Long[] result = new Long[n];
        for (int i = 0; i < n; i++)
            result[i] = dis.readLong();

        dis.close();
        return result;
    }


}
