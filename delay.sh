delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
    tc qdisc add dev veth$1b root netem delay $2ms

}
delays 1 80
delays 2 80
delays 3 75
delays 4 80
delays 5 75
delays 6 80
delays 7 80
#for ((i = 2; i <= 7; i++)); do
#    delays $i 80
#done
delays 8 175
delays 9 20
