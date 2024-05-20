#sudo ip netns add veclusta
#sudo ip netns add veclustb
#sudo ip link add veth0a type veth peer name veth0b
#sudo ip link set veth0a netns veclusta
#sudo ip link set veth0b netns veclustb
#
#sudo ip netns exec veclusta ip link add brlo type bridge 
#sudo ip netns exec veclustb ip link add brlo type bridge 
#
#sudo ip netns exec veclusta ip addr add 192.168.0.2/16 dev brlo
#sudo ip netns exec veclustb ip addr add 192.168.0.3/16 dev brlo
#
#sudo ip netns exec veclustb ip link set veth0b master brlo
#sudo ip netns exec veclusta ip link set veth0a master brlo
#
#sudo ip netns exec veclustb ip link set veth0b up
#sudo ip netns exec veclusta ip link set veth0a up
#sudo ip netns exec veclustb ip link set brlo up
#sudo ip netns exec veclusta ip link set brlo up
#sudo ip netns exec veclustb ip link set lo up
#sudo ip netns exec veclusta ip link set lo up


ip link add brclust type bridge
ip link set brclust up

setup_namespace() {
    ip netns add clust$1
    ip link add veth$1a type veth peer name veth$1b
    ip link set veth$1a netns clust$1
    ip link set veth$1b master brclust
    ip netns exec clust$1 ip addr add 192.168.0.$(( $1 + 1 ))/16 dev veth$1a
    ip netns exec clust$1 ip link set veth$1a up
    ip link set veth$1b up
}

echo "Creating namespaces"
for ((i = 0; i <= 5; i++)); do
    setup_namespace $i
done

iptables -A FORWARD -o brclust -m comment --comment "allow packets to pass from lxd lan bridge" -j ACCEPT
iptables -A FORWARD -i brclust -m comment --comment "allow input packets to pass to lxd lan bridge" -j ACCEPT



#sudo ip link add brtest type bridge 
#sudo ip addr add 192.168.0.1/16 dev brtest
#sudo ip link set veth0b master brtest
#sudo bridge link set dev veth0b hairpin on
#
#
#sudo ip link set brtest up
# 333333333333333333333333333333333333333333333333333333333333333333333333

#sudo ip netns add veclust0
#sudo ip netns add veclust1
#sudo ip link add veth0a type veth peer name veth0b
#sudo ip link add veth1a type veth peer name veth1b
#
#sudo ip link set veth0a netns veclust0
#sudo ip link set veth1a netns veclust1
#
#sudo ip link add brlo type bridge
#
#sudo ip link set veth0b master brlo
#sudo ip link set veth1b master brlo
#
#sudo ip netns exec veclust0 ip addr add 192.168.0.2/16 dev veth0a
#sudo ip netns exec veclust1 ip addr add 192.168.0.3/16 dev veth1a
#
#sudo ip link set brlo up
#sudo ip link set veth1b up
#sudo ip link set veth0b up
#sudo ip netns exec veclust0 ip link set veth0a up
#sudo ip netns exec veclust1 ip link set veth1a up
