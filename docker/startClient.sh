#!/bin/bash 


END_OF_IP=230
CLI_ID=1022

for client in {1..20};
    do
	docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.$END_OF_IP --name=bft.cli.$CLI_ID tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyClient $CLI_ID 1 100000 0 0 false false &
	((END_OF_IP++))
	((CLI_ID++))
    done

