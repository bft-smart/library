SMaRt v0.4
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

2.) The system configurations also have to be specified (see config/system.config). Most of the parameters are
self explanatory.

You can run the counter demonstration by executing the script "runscripts/launch_CounterDemo.bat". This script can be easily adapted to run the other demos. Note that this script only runs in Windows, but it is easy to derive a shell script that runs in other operating systems.

 
Feel free to contact us if you have any questions.

