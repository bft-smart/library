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
	do	gnome-terminal -e "./run_cs ${LIST_IDS[$I]}" --title="Servidor $I" ; sleep 5
done
echo "---------------------------"
echo " "

echo "--- Wait 10s to launch clients ---"
sleep 10
echo "----------------------------------------"
echo " "

echo "--- Launch clients ---"
gnome-terminal --tab -e "./run_cc 1001 1 1000000" --title="Cliente1" --tab -e "./run_cc 1002 1 1000000" --title="Cliente2" --tab -e "./run_cc 1003 1 1000000" --title="Cliente3"  --tab -e "./run_cc 1004 1 1000000" --title="Cliente4"  --tab -e "./run_cc 1005 1 1000000" --title="Cliente5"  --tab -e "./run_cc 1006 1 1000000" --title="Cliente6" --tab -e "./run_cc 1007 1 1000000" --title="Cliente7" --tab -e "./run_cc 1008 1 1000000" --title="Cliente8" --tab -e "./run_cc 1009 1 1000000" --title="Cliente9" --tab -e "./run_cc 1010 1 1000000" --title="Cliente10";
