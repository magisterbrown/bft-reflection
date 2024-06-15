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
import java.util.PriorityQueue;
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
                //System.out.println(Arrays.toString(execManager.getConsensuses().toArray()));
                Long[] writeSaved = writeLatencies.clone();
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
                    boolean printer = !analyzed.contains(cid);
                    analyzed.add(cid);
                    double coeff = 1.0;
                    for (int i = 0; i < ep.getWriteTimes().length; i++) {
                        if(i==my_index || propose[leader_index][my_index] == Monitor.MISSING_VALUE || latestPropose[leader_index][my_index] == Monitor.MISSING_VALUE)
                            continue;

                        long West =  proposeTime - propose[leader_index][my_index] + (propose[leader_index][i] + write[i][my_index]);
                        long WestNew = proposeTime - latestPropose[leader_index][my_index] + (latestPropose[leader_index][i] + latestWrite[i][my_index]);

                        if(ep.getWriteSetted()[i]){
                            long delay = ep.getWriteTimes()[i] - West;
                            double ratio = (double) delay/(propose[leader_index][i] + write[i][my_index]);
                            if(ratio>coeff){
                                if(leader_index == currLeader)
                                    leadDelayed.add(i);
                                long tmp = writeLatencies[i];
                                writeLatencies[i]=Math.max(writeLatencies[i], ep.getWriteTimes()[i]-(proposeTime - latestPropose[leader_index][my_index] + (latestPropose[leader_index][i]));
                            }
                        }
                        else{
                            if(WestNew+(latestPropose[leader_index][i]+latestWrite[i][my_index])*coeff<acceptTime) {
                                writeLatencies[i] = Monitor.MISSING_VALUE;
                                if(leader_index == currLeader)
                                    leadDelayed.add(i);
                            }
                        }
                    }
                    acceptLoop:
                    for (int i = 0; i < ep.getAcceptTimes().length; i++) {

                        if(i==my_index)
                            continue;
                        int earIdx = 0;
                        long earliest = 0;
                        int counter = 0;

                        ep.getWriteTimes();
                        long[] estWriteArrival = new long[ep.getWriteTimes().length];
                        PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> Long.compare(estWriteArrival[a], estWriteArrival[b]));

                        double THRESHOLD = -0.0000000001;

                        int idx = -1;
                        float collectedWeight = 0;
                        for(int j=0;j<ep.getWriteTimes().length; j++){
                            if(!ep.getWriteSetted()[j] || write[j][my_index] == Monitor.MISSING_VALUE)
                                continue;
                            estWriteArrival[j] = ep.getWriteTimes()[j] - write[j][my_index] + write[j][i];
                            pq.add(j);
                        }
                        while(collectedWeight - ((double) svc.getOverlayQuorum()) <= THRESHOLD){
                            if(pq.isEmpty())
                                continue acceptLoop;
                            idx = pq.poll();
                            collectedWeight += ep.getSumWeightsWrite()[idx];
                        }
                        long Aest = estWriteArrival[idx] + write[i][my_index];

                        pq.clear();
                        collectedWeight = 0;
                        int idxn = -1;
                        for(int j=0;j<ep.getWriteTimes().length; j++){
                            if(!ep.getWriteSetted()[j] || latestWrite[j][my_index] == Monitor.MISSING_VALUE)
                                continue;
                            estWriteArrival[j] = ep.getWriteTimes()[j] - latestWrite[j][my_index] + latestWrite[j][i];
                            pq.add(j);
                        }
                        while(collectedWeight - ((double) svc.getOverlayQuorum()) <= THRESHOLD){
                            if(pq.isEmpty())
                                continue acceptLoop;
                            idxn = pq.poll();
                            collectedWeight += ep.getSumWeightsWrite()[idxn];
                        }
                        long AestNew = estWriteArrival[idxn] + latestWrite[i][my_index];

                        if(ep.getAcceptSetted()[i]) {
                            long delay = ep.getAcceptTimes()[i] - Aest;
                            double ratio = (double) delay/(write[idx][i] + write[i][my_index]);
                            if(ratio>coeff){
                                long tmp = writeLatencies[i];
                                writeLatencies[i]=Math.max(writeLatencies[i], ep.getAcceptTimes()[i]-estWriteArrival[idxn]);
                                if(writeLatencies[i]>tmp)
                                    System.out.println("Accept to "+i+" increased by "+NsToS(writeLatencies[i]-tmp));
                            }
                        }
                        else {
                            if(AestNew+(latestWrite[idxn][i]+latestWrite[i][my_index])*coeff<cons.getDecision().firstMessageProposed.decisionTime) {
                                writeLatencies[i] = Monitor.MISSING_VALUE;
                                System.out.println("Accept maxed to "+i);
                            }
                        }

                    }
                } 
                for(int i=0;i<writeSaved.length;i++){
                    if(writeLatencies[i]>writeSaved[i])
                        System.out.println("Dealay to: "+i+" increased by: "+ NsToS(writeLatencies[i]-writeSaved[i]));
                }


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
