#!/bin/bash 

TYPE=$1
FAULTS=$2
END_OF_IP=100
ETHX=$3
MASTER=$4

if [ $# != 4 ]
    then
        echo "Usage: $0 <MicroBenchmark |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init: true|false>"
        echo "Example: bash startCluster.sh MicroBenchmark 3 eth0 true // if it is to init the swarm, first time execution."
	echo "Example: bash startCluster.sh MicroBenchmark 3 eth0 false // swarm already started."
        echo "Shal exist one master swarm which will initialize and link other workers together."
        echo "Currently workers: core2"
        echo ""
        sleep 1
    exit 1
fi

if [ $4 == true ]
    then 
        echo "Leaving swarm."
        docker swarm leave --force
        
        echo "Initializing a new swarm and saving its token at bft-network.token."
	MY_IP=`/sbin/ifconfig $ETHX | grep 'inet addr' | cut -d: -f2 | awk '{print $1}'`
        #docker swarm init --advertise-addr $ETHX |grep 'docker \|--token \|'$MY_IP >  bft-network.token
	docker swarm init --advertise-addr $ETHX |grep "docker swarm join --token" >  bft-network.token

        cat ./bft-network.token
#exit 1
        echo "Creating network overlay: name:bft-network, subnet:10.1.1.0/24, gateway:10.1.1.1."
        docker network create -d overlay --attachable bft-network --driver overlay --subnet=10.1.1.0/24 --gateway=10.1.1.1  > /dev/null
        joinCmd=`cat ./bft-network.token`
        
        worker="core2"
	#for worker in {"core2","null"}; do
                echo "Leaving swarm on "$worker"."
                ssh $worker docker swarm leave --force
                echo "Joining swarm on "$worker"."
                ssh $worker $joinCmd
		sleep 1
        #done
        
    fi
    
echo "Removing all containers."
bash removeAllContainers.sh

case "$TYPE" in
    MicroBenchmark)
        echo ""
        echo "Starting MicroBenchmark Server with f=$FAULTS."
	
	case "$FAULTS" in
		1)
		VRS=v6_f1
		    for replica in {0..1};
            	    do
                	echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
			docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.$END_OF_IP --name=bft.relica.$replica tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyServer $replica 100000 0 0 false & > /dev/null
                	((END_OF_IP++))
                	sleep 1
	    	    done
		;;
		*)
		    echo "Usage: $0 <MicroBenchmark |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init:  true|false>"
	            echo "Example: bash startCluster.sh CounterServer 3 eth0 false"
    		    exit 1
	esac
	echo ""
	echo "Now run the client."
	echo "docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.220 --name=bft.cli.1001 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyClient <clientID (initial)> <Num clients> <n ops> <payload req> <payload reply> false false"
	echo "docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.220 --name=bft.cli.1001 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyClient 1001 1 1000 0 0 false false"
	echo "docker run --net bft-network tulioribeiro/bft-smart:$VRS  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterClient  1001 1 200"
	echo ""

    ;;
    
    
    YCSB)
        echo ""
        echo "Starting YCSB with f=$FAULTS."
	
	case "$FAULTS" in

		1)
		VRS=v4_f1
		    for replica in {0..3};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --net bft-network --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.ycsb.YCSBServer $replica  & >> /dev/null
                    ((END_OF_IP++))
                    sleep 1
	    	    done
		;;
		*)
		    echo "Usage: $0 <CounterServer |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init:  true|false>"
	        echo "Example: bash startCluster.sh CounterServer 3 eth0"
    		exit 1
	esac
	echo ""
	echo "Now run the client: ID_Client range [1000 - 1500]. You can create more keys."
	echo "docker run --net bft-network tulioribeiro/bft-smart:$VRS  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* com.yahoo.ycsb.Client -threads 4 -P config/workloads/workloada -p measurementtype=timeseries -p timeseries.granularity=1000 -db bftsmart.demo.ycsb.YCSBClient -s > output_YCSB.txt"
	echo ""

    ;;
    
    *)
	echo "Usage: $0 <MicroBenchmark |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init:  true|false>"
    echo "Example: bash startCluster.sh CounterServer 3 eth0 false"
    exit 1
esac

echo ""
echo " INITIALIZED"


