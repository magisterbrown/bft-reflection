undelay() {
    ip netns exec clust$1 tc qdisc del dev veth$1a root 
    tc qdisc del dev veth$1b root 
}
for ((i = 1; i <= 9; i++)); do
    undelay $i
done

