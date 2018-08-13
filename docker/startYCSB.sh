#!/bin/bash 

#FAULTS=1
#N=$(( 3 * FAULTS + 1 ))
VRS=v3_f3

    for replica in {0..9};
        do 
	    echo "Starting replica:" $replica
	    docker run -m 124m bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.ycsb.YCSBServer  $replica  >> /dev/null &
    	    sleep 1
	done 

echo " INITIALIZED"

#CLIENT VARS
ID=1001
INC=1
REPETITIONS=100
echo "Now run:"
echo "docker run bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* com.yahoo.ycsb.Client -threads 4 -P config/workloads/workloada -p measurementtype=timeseries -p timeseries.granularity=1000 -db bftsmart.demo.ycsb.YCSBClient -s > output_YCSB.txt"


