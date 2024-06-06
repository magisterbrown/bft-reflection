delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
    tc qdisc add dev veth$1b root netem delay $2ms

}
delays 1 75
delays 2 75
delays 3 70 
delays 4 75
delays 5 70
delays 6 75
delays 7 75
#for ((i = 2; i <= 7; i++)); do
#    delays $i 75
#done
delays 8 130 
delays 9 10
