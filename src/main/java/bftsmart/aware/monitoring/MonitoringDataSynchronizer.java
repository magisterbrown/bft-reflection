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
                //TODO: manage changing viewN
                Long[] writeLatencies = Monitor.getInstance(svc).getFreshestWriteLatencies();
                Long[] proposeLatencies = Monitor.getInstance(svc).getFreshestProposeLatencies();

                int viewN = svc.getCurrentViewN();
                int my_index = svc.getStaticConf().getProcessId();
                long[] delays = new long[viewN];
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

                    int leader_index = cons.getDecision().getLeader();
                    //leader_index = 0;
                    if(leader_index < 0 || leader_index >= propose.length || leader_index==3 || leader_index == 5)
                        continue;

                    long proposeTime = cons.getDecision().firstMessageProposed.proposeReceivedTime;
                    long acceptTime = cons.getDecision().firstMessageProposed.acceptSentTime;
                    for (int i = 0; i < ep.getWriteTimes().length; i++) {
                        if(i==my_index || propose[leader_index][my_index] == Monitor.MISSING_VALUE)
                            continue;
                        long delay = 0;
                        long estPropSent = proposeTime - propose[leader_index][my_index];
                        long est =  estPropSent + (propose[leader_index][i] + write[i][my_index]);
                        long real = ep.getWriteTimes()[i];
                        String ms;
                        if(ep.getWriteSetted()[i]){
                            delay = real - est;
                            ms = "Delayed by ";  
                        }
                        else{
                            //if(acceptTime>0)
                            //    real = acceptTime;
                            //else
                            //    real = Monitor.MISSING_VALUE;
                            delay = Monitor.MISSING_VALUE;
                            ms = "Did not arrive: ";
                        }
                        if(delay>writeLatencies[i]){
                                System.out.println("Leader: "+leader_index);
                                System.out.println(ms + delay + " from: " + i);
                                System.out.println("Propose: "+ proposeTime);
                                System.out.println("Est Propose: "+ estPropSent);
                                System.out.println("Expected: "+ NsToS(est-estPropSent));
                                System.out.println("Actual: "+ NsToS(real-estPropSent));
                                System.out.println("\n");
                        }
                        //if(delays[i]<delay)
                        //    delays[i] = delay;
                        //if(delay>writeLatencies[i] && proposeLatencies[i] + writeLatencies[i] < real-estPropSent){
                        //    writeLatencies[i]+=(real-estPropSent)-(writeLatencies[i]+proposeLatencies[i]);
                        //    System.out.println("Latency increased: " + NsToS(writeLatencies[i]) + " to: " + i);
                        //}
                    }
                }
                //BYZANTINE nodes:
                Long lat = (long) 23;
                if(my_index == 3 ){
                    //Arrays.fill(writeLatencies, (long)22);
                    //Arrays.fill(proposeLatencies, (long)22);
                    writeLatencies[5] = lat;
                    proposeLatencies[5] = lat;
                }
                if(my_index == 5){
                    //Arrays.fill(writeLatencies, (long)22);
                    //Arrays.fill(proposeLatencies, (long)22);
                    writeLatencies[3] = lat;
                    proposeLatencies[3] = lat;
                }
                //for(int i=0; i<viewN;i++){
                //    System.out.println("Delayed by: " + NsToS(delays[i]) + " from: " + i);
                //}

                // Get freshest write latenciesfrom Monitor

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
