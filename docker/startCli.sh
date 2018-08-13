#!/bin/bash 

for cli in {1000..1100};
    do
        echo "Starting client, ID="$cli", Version: bft-smart:v4_f3"
        docker run --net bft-overlay tulioribeiro/bft-smart:v4_f3  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterClient  $cli 1 1000 & 
    done
