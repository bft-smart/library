#!/bin/bash


LOCAL_IP="127.0.0.1"
MIN_PORT=11000
I_PORT=0
J=0
J_PORT=0
HOSTS_CONFIG="config/hosts.config"

rm -f config/currentView
cp -f config/hosts-orig.config $HOSTS_CONFIG

echo "--- Launch servers ---"
for I in 0 1 2 3 
	do	gnome-terminal -e "./run_cs $I" --title="Servidor $I" ; sleep 5
done
echo "---------------------------"
echo " "

echo "--- Wait 10s to launch clients ---"
sleep 10
echo "----------------------------------------"
echo " "

READ_ONLY="false"

echo "--- Launch clients ---"
#./run_cc 1 1001 true 60

gnome-terminal --tab -e "./run_cc 1 1001 true 300" --title="Clientes" &
#gnome-terminal --tab -e "./run_cc 1 1001 $READ_ONLY" --title="Clientes" &

echo "--------------------------"
echo " "

for I in 4 5 6 7 8 9 10 11 12
do
	let "I_PORT = MIN_PORT + (I * 10)"
	echo "-----------Test server $I-------------"
	echo "Wait 60s to launch a new server"
	sleep 60
	echo "${I} ${LOCAL_IP} ${I_PORT}" >> $HOSTS_CONFIG

	echo "Launch new server"
	gnome-terminal -e "./run_cs $I" --title="Servidor $I";

	echo "Wait 10s to launch TTP to join the new server"
	sleep 10

	echo "Join new server"
	gnome-terminal -e "./run_ttpServices ${I} ${LOCAL_IP} ${I_PORT}" --title="TTP join";

	echo ""
	echo "Wait 30s "
	sleep 30
	echo ""
	
        let "J = I - 4"
        echo "Disjoint server ${J}"
	let "J_PORT = MIN_PORT + (J * 10)"
	sed -i "/${J_PORT}/d" $HOSTS_CONFIG
        gnome-terminal -e "./run_ttpServices ${J}" --title="TTP disjoint"

done

