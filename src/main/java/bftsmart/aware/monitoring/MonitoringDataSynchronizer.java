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
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

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

            Set<Integer> analyzed = new HashSet<Integer>();
            Set<Integer> leadDelayed;
            HashMap<Integer, Set<Integer>> susLeaders;

            @Override
            public void run() {
                susLeaders = new HashMap<>();
                leadDelayed = new HashSet<>();
                int currLeader = execManager.getCurrentLeader();
                susLeaders.put(currLeader, new HashSet<>());
                //TODO: manage changing viewN
                Monitor latest = Monitor.getInstance(svc);
                Long[] writeLatencies = latest.getFreshestWriteLatencies();
                Long[] proposeLatencies = latest.getFreshestProposeLatencies();

                Long[][] latestPropose = latest.sanitize(latest.getM_propose());
                Long[][] latestWrite = latest.sanitize(latest.getM_write());
                if (!svc.getStaticConf().isUseDummyPropose())
                    latestPropose = latestWrite;



                int viewN = svc.getCurrentViewN();
                int my_index = svc.getStaticConf().getProcessId();
                long[] delays = new long[viewN];
                Arrays.fill(delays, 0);
                System.out.println("Under review");
                System.out.println(Arrays.toString(execManager.getConsensuses().toArray()));
                for(Integer cid: List.copyOf(execManager.getConsensuses())){
                    if(cid == -1)
                        continue;
                    Consensus cons = execManager.getConsensus(cid);
                    Epoch ep = cons.getDecisionEpoch();
                    if(ep == null)
                        continue;
                    if(!ep.shouldVerifyLatency())
                        continue;
                    if(!cons.isDecided())
                        continue;

                    Monitor oldMonitor = Monitor.getInstance(ep.getController());

                    Long[][] propose = oldMonitor.sanitize(oldMonitor.getM_propose());
                    Long[][] write = oldMonitor.sanitize(oldMonitor.getM_write());
                    if (!svc.getStaticConf().isUseDummyPropose())
                        propose = write;

                    int leader_index = cons.getDecision().getLeader();
                    //leader_index = 0;
                    if(leader_index < 0 || leader_index >= propose.length )
                        continue;
                    if(cons.getDecision().firstMessageProposed == null)
                        continue;
                    long proposeTime = cons.getDecision().firstMessageProposed.proposeReceivedTime;
                    if(proposeTime==0)
                        continue;
                    long acceptTime = cons.getDecision().firstMessageProposed.acceptSentTime;
                    //System.out.println("Accept diff " + (acceptTime - ep.acceptTime));
                    //System.out.println("Accept diff " + NsToS(acceptTime - ep.acceptTime));
                    boolean printer = !analyzed.contains(cid);
                    analyzed.add(cid);
                    //printer=true;
                    if(false)
                        System.out.println("Consensus: "+cid);
                    for (int i = 0; i < ep.getWriteTimes().length; i++) {
                        if(i==my_index || propose[leader_index][my_index] == Monitor.MISSING_VALUE)
                            continue;
                        long delay = 0;
                        long estPropSent = proposeTime - propose[leader_index][my_index];
                        long West =  estPropSent + (propose[leader_index][i] + write[i][my_index]);
                        long real;
                        String ms;
                        if(ep.getWriteSetted()[i]){
                            real = ep.getWriteTimes()[i];
                            ms = "Delayed by ";  
                        }
                        else{
                            ms = "Did not arrive: ";
                            if(West<acceptTime) {
                                writeLatencies[i] = Monitor.MISSING_VALUE;
                                if(!susLeaders.containsKey(leader_index))
                                    susLeaders.put(leader_index, new HashSet<>());
                                susLeaders.get(leader_index).add(i);
                            }
                            continue;
                        }
                        delay = real - West;
                        double ratio = (double) delay/(propose[leader_index][i] + write[i][my_index]);
                        if(ratio>0.1){
                                System.out.println("Ration: "+ratio);
                                if(!susLeaders.containsKey(leader_index))
                                    susLeaders.put(leader_index, new HashSet<>());
                                susLeaders.get(leader_index).add(i);
                                if(leader_index == currLeader)
                                    leadDelayed.add(i);
                                if(false){
                                    System.out.println("I am: " + my_index);
                                    System.out.println("Leader: "+leader_index);
                                    System.out.println(ms + delay + " from: " + i + " "+ NsToS(delay));
                                    System.out.println("Propose: "+ proposeTime);
                                    System.out.println("Est Propose: "+ estPropSent);
                                    System.out.println("Expected: "+ NsToS(West-estPropSent));
                                    System.out.println("Actual: "+ NsToS(real-estPropSent));
                                    System.out.println("\n");
                                }
                                long should = real - (proposeTime - latestPropose[leader_index][my_index] + latestPropose[leader_index][i]);
                                writeLatencies[i]=Math.max(writeLatencies[i], should);

                                //if(latestPropose[leader_index][i] + writeLatencies[i] < real-estPropSent){
                                //    writeLatencies[i]+=(real-estPropSent)-(writeLatencies[i]+latestPropose[leader_index][i]);
                                //    if(printer)
                                //        System.out.println("Latency increased: " + NsToS(writeLatencies[i]) + " to: " + i);
                                //}
                        }
                        //if(delays[i]<delay)
                        //    delays[i] = delay;
                    }
                }
                System.out.println("Monitorere: " + this.hashCode());
                System.out.println("Sus leaders:");
                for (int key : susLeaders.keySet())
                    System.out.print(", "+ key + ": " + susLeaders.get(key).size());
                System.out.println("");
                System.out.println("Curr leader: "+ currLeader + ": " + susLeaders.get(currLeader).size());
                System.out.println("Curr leader: "+ currLeader + ": " + leadDelayed.size());
                if(leadDelayed.size() > svc.getStaticConf().getF())
                    System.out.println("Leader CHAAAANGE");
                //BYZANTINE nodes:
                //Long lat = (long) 23;
                //if(my_index == 2 ){
                //    //Arrays.fill(writeLatencies, (long)22);
                //    //Arrays.fill(proposeLatencies, (long)22);
                //    writeLatencies[4] = lat;
                //    proposeLatencies[4] = lat;
                //}
                //if(my_index == 4){
                //    //Arrays.fill(writeLatencies, (long)22);
                //    //Arrays.fill(proposeLatencies, (long)22);
                //    writeLatencies[2] = lat;
                //    proposeLatencies[2] = lat;
                //}
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
