BFT-SMaRt v0.8
----------

This package contains the BFT-SMaRt source code (src/), binary file (bin/), libraries needed (lib/), documentation (doc/), and configuration files (config/).
BFT-SMaRt requires the Java Runtime Environment version 1.7 or later.

-------------------------
To run any demonstration you first need to configure BFT-SMaRt to define the protocol behavior and the location of
each replica.

1.) The servers must be specified in the configuration file (see config/hosts.config). An example:

#server id, address and port (the ids from 0 to n-1 are the service replicas) 
0 127.0.0.1 11000
1 127.0.0.1 11010
2 127.0.0.1 11020
3 127.0.0.1 11030

2.) The system configurations also have to be specified (see config/system.config). Most of the parameters are self explanatory.

You can run the counter demonstration by executing the following commands, from within the main folder:

#Start the servers (4 replicas, to tolerate 1 fault)
runscripts\smartrun.bat bftsmart.demo.counter.CounterServer 0
runscripts\smartrun.bat bftsmart.demo.counter.CounterServer 1
runscripts\smartrun.bat bftsmart.demo.counter.CounterServer 2
runscripts\smartrun.bat bftsmart.demo.counter.CounterServer 3

#Start a client

runscripts\smartrun.bat bftsmart.demo.counter.CounterClient 1001 <increment> [<number of operations>]

#if <increment> equals 0 the request will be read-only
#default <number of operations> equals 1000

You can use the "runsmart.bat" script in Windows, and the "runsmart.sh" script em Linux.
When running the script in Linux it is necessary to set the permissions to execute the script with the command "chmod +x runsmart.sh".
These scripts can be easily be adapted to run other demos.

Other demo options are:
- Random demo. You can run it by using the RandomServer and RandomClient classes located in the package bftsmart.demo.random.
- BFTMap. A Table of hash maps where tables can be created and key value pair added to it.
  The server is bftmap.demo.bftmap.BFTMapServer and the clients are BFTMapClient for incremental inserts or BFTMapInteractiveClient for a command line client.
  Parameters to run the BFTMap demo are displayed when attempts to start the servers and clients are made without parameters.

This version of BFT-SMaRt implements a state transfer protocol, which is already pretty robust.
You can activate/deactivate it by editing the "config/system.config" file, and setting the parameter "system.totalordermulticast.state_transfer" to "false".

This version also implements a reconfiguration protocol, that you can use to add/remove replicas from the initial group. This protocol is still experimental.

Finally, we have also implemented a new version of the leader change protocol, which is also experimental.
 
Feel free to contact us if you have any questions.