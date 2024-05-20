delays() {
    ip netns exec clust$1 tc qdisc add dev veth$1a root netem delay $2ms
}
for ((i = 0; i <= 3; i++)); do
    delays $i 50
done
delays 4 400 
