#!/bin/bash 

TYPE=$1
FAULTS=$2
END_OF_IP=100
ETHX=$3
MASTER=$4

if [ $# != 4 ]
    then
        echo "Usage: $0 <CounterServer |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init: true|false>"
        echo "Example: bash startCluster.sh CounterServer 3 eth0"
        echo "Shal exist one master swarm which will initialize and link other workers together."
        echo "Currently workers: s12, s14"
        echo ""
        sleep 1
    exit 1
fi

if [ $4 == true ]
    then 
        echo "Leaving swarm."
        docker swarm leave --force
        
        echo "Initializing a new swarm and saving its token at bft-overlay.token."
        docker swarm init --advertise-addr $ETHX | grep "docker swarm join --token" > bft-overlay.token
        cat ./bft-overlay.token
        
        echo "Creating network overlay: name:bft-overlay, subnet:10.1.1.0/24, gateway:10.1.1.1."
        docker network create -d overlay --attachable bft-overlay --driver overlay --subnet=10.1.1.0/24 --gateway=10.1.1.1  > /dev/null
        joinCmd=`cat ./bft-overlay.token`
        
        for worker in {"s12","s14"}; do 
                echo "Leaving swarm on "$worker"."
                ssh $worker docker swarm leave --force
                echo "Joining swarm on "$worker"."
                ssh $worker $joinCmd
        done
        
    fi
    
echo "Removing all containers."
bash removeAllContainers.sh

case "$TYPE" in
    CounterServer)
        echo ""
        echo "Starting CounterServer with f=$FAULTS."
	
	case "$FAULTS" in

		1)
		VRS=v4_f1
		    for replica in {0..3};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --expose=12000 --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterServer $replica  & >> /dev/null
                    ((END_OF_IP++))
                    sleep 1
	    	    done
		;;
		3)

		VRS=v4_f3
		    for replica in {0..4};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --expose=12000 --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterServer $replica  & >> /dev/null
                    ((END_OF_IP++))
                    sleep 2
	    	    done
		;;

		10)
		VRS=v4_f10
		    for replica in {0..30};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterServer $replica  & >> /dev/null
                    ((END_OF_IP++))
                    sleep 2                    
	    	    done
		;;
		*)
		    echo "Usage: $0 <CounterServer |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init:  true|false>"
            echo "Example: bash startCluster.sh CounterServer 3 eth0 false"
    		exit 1
	esac
	echo ""
	echo "Now run the client: ID_Client range [1000 - 1500]. You can create more keys."
	echo "docker run --net bft-overlay tulioribeiro/bft-smart:$VRS  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterClient  <ID_Client> <Increment> <Repetitions>"
	echo "docker run --net bft-overlay tulioribeiro/bft-smart:$VRS  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.counter.CounterClient  1001 1 200"
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
                    docker run --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.ycsb.YCSBServer $replica  & >> /dev/null
                    ((END_OF_IP++))
                    sleep 1
	    	    done
		;;
		3)

		VRS=v4_f3
		    for replica in {0..9};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.ycsb.YCSBServer $replica  & >> /dev/null
                    sleep 1
                    ((END_OF_IP++))
	    	    done
		;;

		10)
		VRS=v4_f10
		    for replica in {0..30};
                do
                    echo "Starting: R"$replica", Version: bft-smart:"$VRS," IP:10.1.1."$END_OF_IP
                    docker run --net bft-overlay --ip 10.1.1.$END_OF_IP tulioribeiro/bft-smart:$VRS java -cp /opt/BFT-SMaRt.jar:/opt/lib/* bftsmart.demo.ycsb.YCSBServer $replica  & >> /dev/null
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
	echo "docker run --net bft-overlay tulioribeiro/bft-smart:$VRS  java -cp /opt/BFT-SMaRt.jar:/opt/lib/* com.yahoo.ycsb.Client -threads 4 -P config/workloads/workloada -p measurementtype=timeseries -p timeseries.granularity=1000 -db bftsmart.demo.ycsb.YCSBClient -s > output_YCSB.txt"
	echo ""

    ;;
    
    *)
	echo "Usage: $0 <CounterServer |  YCSB> <n tolerated faults: 1 | 3 | 10> < interface: eth0|em1> <swarm init:  true|false>"
    echo "Example: bash startCluster.sh CounterServer 3 eth0 false"
    exit 1
esac

echo ""
echo " INITIALIZED"


