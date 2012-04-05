#!/bin/bash


LOCAL_IP="127.0.0.1"
MIN_PORT=11000
I_PORT=0
J=0
J_PORT=0
HOSTS_CONFIG="config/hosts.config"

LIST_IDS=( 0 1 2 3 )
NUMBER_SERVERS=3

rm -f config/currentView
rm -f logs/*
cp -f config/hosts-orig.config $HOSTS_CONFIG

echo "--- Launch servers ---"
for I in 0 1 2 3
	do	gnome-terminal -e "script -c './run_kv_server.sh ${LIST_IDS[$I]}' logs/server_${LIST_IDS[$I]}.txt" --title="Servidor $I" ; sleep 5
done
echo "---------------------------"
echo " "

echo "--- Wait 10s to launch clients ---"
sleep 10
echo "----------------------------------------"
echo " "

echo "--- Launch clients ---"
gnome-terminal --tab -e "script -c './run_kv_client.sh 1001 1 1000000' logs/client_1001.txt" --title="Cliente1" --tab -e "script -c './run_kv_client.sh 1002 1 1000000' logs/client_1002.txt" --title="Cliente2" --tab -e "script -c './run_kv_client.sh 1003 1 1000000' logs/client_1003.txt" --title="Cliente3"  --tab -e "script -c './run_kv_client.sh 1004 1 1000000' logs/client_1004.txt" --title="Cliente4"  --tab -e "script -c './run_kv_client.sh 1005 1 1000000' logs/client_1005.txt" --title="Cliente5"  --tab -e "script -c './run_kv_client.sh 1006 1 1000000' logs/client_1006.txt" --title="Cliente6" --tab -e "script -c './run_kv_client.sh 1007 1 1000000' logs/client_1007.txt" --title="Cliente7" --tab -e "script -c './run_kv_client.sh 1008 1 1000000' logs/client_1008.txt" --title="Cliente8" --tab -e "script -c './run_kv_client.sh 1009 1 1000000' logs/client_1009.txt" --title="Cliente9" --tab -e "script -c './run_kv_client.sh 1010 1 1000000' logs/client_1010.txt" --title="Cliente10";
