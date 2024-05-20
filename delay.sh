delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
    tc qdisc add dev veth$1b root netem delay $2ms

}
for ((i = 1; i <= 7; i++)); do
    delays $i 50
done
delays 8 400 
