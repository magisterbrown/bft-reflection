delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
    tc qdisc add dev veth$1b root netem delay $2ms

}
delays 1 75
for ((i = 2; i <= 9; i++)); do
    delays $i 200
done
