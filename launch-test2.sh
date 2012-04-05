#!/bin/bash


LOCAL_IP="127.0.0.1"
MIN_PORT=11000
I_PORT=0
J=0
J_PORT=0
HOSTS_CONFIG="config/hosts.config"

LIST_IDS=( 0 1 2 3 )
NUMBER_SERVERS=3

CLIENTS_NUM=10
CLIENTS_IDS=1001
CLIENTS_WRITE="true"

#In minutes
RUN=180
let "CLIENTS_RUNTIME = 60 * $RUN"

NOW=`date +%s`
let "END = $NOW + $CLIENTS_RUNTIME"


rm -f config/currentView
rm -f logs/*
cp -f config/hosts-orig.config $HOSTS_CONFIG

echo "--- Launch servers ---"
for I in 0 1 2 3
	do	gnome-terminal -e "script -c './run_cs ${LIST_IDS[$I]}' logs/server_${LIST_IDS[$I]}.txt" --title="Servidor $I" ; sleep 5
done
echo "---------------------------"
echo " "

echo "--- Wait 10s to launch clients ---"
sleep 10
echo "----------------------------------------"
echo " "

echo "--- Launch clients ---"
#gnome-terminal --tab -e "./run_cc 1001" --title="Cliente1" --tab -e "./run_cc 1002" --title="Cliente2" --tab -e "./run_cc 1003" --title="Cliente3"  --tab -e "./run_cc 1004" --title="Cliente4"  --tab -e "./run_cc 1005" --title="Cliente5"  --tab -e "./run_cc 1006" --title="Cliente6" --tab -e "./run_cc 1007" --title="Cliente7" --tab -e "./run_cc 1008" --title="Cliente8" --tab -e "./run_cc 1009" --title="Cliente9" --tab -e "./run_cc 1010" --title="Cliente10";

gnome-terminal --tab -e "script -c './run_cc $CLIENTS_NUM $CLIENTS_IDS $CLIENTS_WRITE $CLIENTS_RUNTIME' logs/clients.txt" --title="Clientes" &


echo "--------------------------"
echo " "

for((I=0; $NOW < $END ; I++))
#for I in 0 1 2 3 4 5 6 7 8 9 10 11
do
	let "POS = NUMBER_SERVERS - (I % ${#LIST_IDS[@]})"
	let "NEW_ID = ${LIST_IDS[$POS]} +1"

	let "I_PORT = MIN_PORT + ( NEW_ID* 10)"

	echo "-----------Test server $I-------------"
	echo "Wait 60s to launch a new server ["${NEW_ID} ${LOCAL_IP} ${I_PORT}"]"
	sleep 60
	echo "${NEW_ID} ${LOCAL_IP} ${I_PORT}" >> $HOSTS_CONFIG

	echo "Launch new server ["$NEW_ID"]"
	gnome-terminal -e "./run_cs $NEW_ID" --title="Servidor $NEW_ID";

	echo "Wait 10s to launch TTP to join the new server ["$NEW_ID"]"
	sleep 10

	echo "TTP joins the new server ["$NEW_ID"]"
	gnome-terminal -e "./run_ttpServices ${NEW_ID} ${LOCAL_IP} ${I_PORT}" --title="TTP join";

	echo ""
	echo "Wait 60s"
	sleep 60
	echo ""

	let "J = ${LIST_IDS[$POS]}"
	let "J_PORT = MIN_PORT + (J * 10)"

	echo "TTP disjoitns the new server ["${J}"]"
	sed -i "/${J_PORT}/d" $HOSTS_CONFIG
       gnome-terminal -e "./run_ttpServices ${J}" --title="TTP disjoint"

	LIST_IDS[$POS]=${NEW_ID}

	NOW=`date +%s`
done
