#/bin/bash

java -cp lib/core-0.1.4.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar:./bin/ com.yahoo.ycsb.Client -threads 10 -P config/workloads/workloada -p measurementtype=timeseries -p timeseries.granularity=1000 -db bftsmart.demo.ycsb.YCSBClient -s > output.txt
# load set to 500 ops/s
#java -cp lib/core-0.1.4.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar:./bin/ com.yahoo.ycsb.Client -threads 10 -target 500 -P config/workloads/workloada -p measurementtype=timeseries -p timeseries.granularity=1000 -db bftsmart.demo.ycsb.YCSBClient -s > output.txt
