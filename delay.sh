delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
    tc qdisc add dev veth$1b root netem delay $2ms

}
delays 1 45
delays 2 45
delays 3 40 
delays 4 45
delays 5 40
delays 6 45
delays 7 45
#for ((i = 2; i <= 4; i++)); do
#    delays $i 45
#done
delays 8 150
delays 9 10
