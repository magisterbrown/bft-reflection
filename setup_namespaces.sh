ip link add brclust type bridge
ip link set brclust up

setup_namespace() {
    ip netns add clust$1
    ip link add veth$1a type veth peer name veth$1b
    ip link set veth$1a netns clust$1
    ip link set veth$1b master brclust
    ip netns exec clust$1 ip addr add 192.168.0.$1/16 dev veth$1a
    ip netns exec clust$1 ip link set veth$1a up
    ip link set veth$1b up
}

echo "Creating namespaces"
for ((i = 1; i <= 9; i++)); do
    setup_namespace $i
done

iptables -A FORWARD -o brclust -m comment --comment "allow packets to pass from lxd lan bridge" -j ACCEPT
iptables -A FORWARD -i brclust -m comment --comment "allow input packets to pass to lxd lan bridge" -j ACCEPT

