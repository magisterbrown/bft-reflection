package bftsmart.aware.monitoring;

import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.ServiceProxy;
import bftsmart.consensus.Epoch;
import bftsmart.consensus.Consensus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.Arrays;

/**
 * This class disseminates this replicas measurements with total order
 *
 * @author cb
 */
public class MonitoringDataSynchronizer {

    private ServiceProxy monitoringDataDisseminationProxy;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static String NsToS(long ns) {
        return String.format("%.4f",Double.valueOf(ns) * 1e-9);
    }


    /**
     * Creates a new Synchronizer to disseminate data with total order
     *
     * @param svc server view controller
     */
    MonitoringDataSynchronizer(ServerViewController svc, ExecutionManager execManager) {

        int myID = svc.getStaticConf().getProcessId();
        monitoringDataDisseminationProxy = new ServiceProxy(myID);

        // Create a time to periodically broadcast this replica's measurements to all replicas
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long[] delays = new long[5];
                Arrays.fill(delays, 0);
                for(Integer cid: List.copyOf(execManager.getConsensuses())){
                    if(cid == -1)
                        continue;

                    Consensus cons = execManager.getConsensus(cid);
                    Epoch ep = cons.getDecisionEpoch();
                    if(ep == null)
                        continue;
                    if(!ep.shouldVerifyLatency())
                        continue;

                    Monitor oldMonitor = Monitor.getInstance(ep.getController());

                    Long[][] propose = oldMonitor.sanitize(oldMonitor.getM_propose());
                    Long[][] write = oldMonitor.sanitize(oldMonitor.getM_write());
                    if (!svc.getStaticConf().isUseDummyPropose())
                        propose = write;

                    int my_index = svc.getStaticConf().getProcessId();
                    int leader_index = cons.getDecision().firstMessageProposed.getSender();
                    if(leader_index < 0 || leader_index >= propose.length)
                        continue;

                    long proposeTime = cons.getDecision().firstMessageProposed.proposeReceivedTime;
                    long writeTime = cons.getDecision().firstMessageProposed.writeSentTime;
                    for (int i = 0; i < ep.getWriteTimes().length; i++) {
                        long delay = 0;
                        long est = proposeTime - propose[leader_index][my_index] + propose[leader_index][i] + write[i][my_index];
                        if(ep.getWriteSetted()[i]){
                            delay = ep.getWriteTimes()[i] - est;
                            //System.out.println("Delayed by: " + NsToS(delay) + " from: " + i);
                        }
                        else{
                            delay = writeTime  - est;
                            System.out.println("Did not arrive for: " + NsToS(delay) + " from: " + i);
                        }
                        if(delays[i]<delay)
                            delays[i] = delay;
                            //if(!delays.containsKey(i) || delays.get(i)<delay)
                            //    delays.put(i, delay);
                            //System.out.println("Delay for write "+ (real-est) + " from " + i);
                            //System.out.println("Prop to write "+ (real-ep.proposeTime));
                            //System.out.println("Real "+real);
                            //System.out.println("Est "+est);
                    }
                }
                for(int i=0; i<5;i++){
                    System.out.println("Delayed by: " + NsToS(delays[i]) + " from: " + i);
                }

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
