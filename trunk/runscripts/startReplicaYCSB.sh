#/bin/bash

REPLICA_INDEX=$1

java -cp bin/:lib/* bftsmart.demo.ycsb.YCSBServer $REPLICA_INDEX
