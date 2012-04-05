SMaRt v0.6
----------

This package contains the SMaRt source code (src/), binary file (bin/), libraries needed (lib/), documentation (doc/), and configuration files (config/).

-------------------------
To run any demonstration you first need to configure SMaRt to define the protocol behaviour and the location of
each replica.

1.) The servers must be specified in the configuration file (see config/hosts.config). An example:

#server id, address and port (the ids from 0 to n-1 are the service replicas) 
0 localhost 11234 
1 localhost 11235 
2 localhost 11236 
3 localhost 11237 

2.) The system configurations also have to be specified (see config/system.config). Most of the parameters are self explanatory.

You can run the counter demonstration by executing the following commands, from within the main folder:

#Start the servers (4 replicas, to tolerate 1 fault)
runscripts\smartrun.bat navigators.smart.tom.demo.counter.CounterServer 0
runscripts\smartrun.bat navigators.smart.tom.demo.counter.CounterServer 1
runscripts\smartrun.bat navigators.smart.tom.demo.counter.CounterServer 2
runscripts\smartrun.bat navigators.smart.tom.demo.counter.CounterServer 3

#Start a client

#if <increment> equals 0 the request will be read-only
#default <number of operations> equals 1000

runscripts\smartrun.bat navigators.smart.tom.demo.counter.CounterClient 1001 <increment> [<number of operations>]

You ca use the "runsmart.bat" script in Windows, and the "runsmart.sh" script em linux. These scripts can be easly be adaptated to run other demos, and you can derived other scripts from these ones to run SMaRt in other operating systems.

Additionally to the counter demo, there is also the random demo. You can run it by using the RandomServer and RandomClient classes located in the package navigators.smart.tom.demo.random.

This version of SMaRt implements a state transfer protocol, which is already pretty robust. You can activate/de-activate it by editing the "config/system.config" file, and setting the parameter "system.totalordermulticast.state_transfer" to "false".

This version also implements a recofiguration protocol, that you can use to had/remove replicas from the initial group. This protocol is still experimental.

Finally, we have also implemented a new version of the leader change protocol, which is also experimental.
 
Feel free to contact us if you have any questions.

