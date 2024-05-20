undelay() {
    ip netns exec clust$1 tc qdisc del dev veth$1a root 
}
for ((i = 0; i <= 4; i++)); do
    undelay $i
done

