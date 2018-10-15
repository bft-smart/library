
#!/bin/bash 

#docker run -t --rm --expose=11000 --net bft-overlay --ip 10.1.1.100 --name=bft.node.0 tulioribeiro/bft-smart:v2_f1  0 > /dev/null
#docker run -t --rm --expose=11000 --net bft-overlay --ip 10.1.1.101 --name=bft.node.1 tulioribeiro/bft-smart:v2_f1  1 > /dev/null
#docker run -t --rm --expose=11000 --net bft-overlay --ip 10.1.1.102 --name=bft.node.2 tulioribeiro/bft-smart:v2_f1  2 > /dev/null
#docker run -t --rm --expose=11000 --net bft-overlay --ip 10.1.1.103 --name=bft.node.3 tulioribeiro/bft-smart:v2_f1  3 > /dev/null


docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.100 --name=bft.node.0 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyServer 0 100000 0 0 false  &
sleep 2
docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.101 --name=bft.node.1 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyServer 1 100000 0 0 false > /dev/null &
sleep 2
docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.102 --name=bft.node.2 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyServer 2 100000 0 0 false > /dev/null &
sleep 2
docker run -t --rm --expose=12000 --net bft-network --ip 10.1.1.103 --name=bft.node.3 tulioribeiro/bft-smart:v6_f1_RSA bftsmart.demo.microbenchmarks.ThroughputLatencyServer 3 100000 0 0 false > /dev/null &



