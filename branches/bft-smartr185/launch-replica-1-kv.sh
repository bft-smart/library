#!/bin/bash


LOCAL_IP="127.0.0.1"
MIN_PORT=11000
I_PORT=0
J=0
J_PORT=0
HOSTS_CONFIG="config/hosts.config"

LIST_IDS=( 0 1 2 3 )
NUMBER_SERVERS=3


echo "--- Launch server 1 ---"
for I in 1
	do	gnome-terminal -e "script -c './run_kv_server.sh ${LIST_IDS[$I]}' logs/server_${LIST_IDS[$I]}.txt" --title="Servidor $I" ; sleep 5
done
