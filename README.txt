WHEAT 0.2-alpha
----------

This branch contains the source code for WHEAT, an extension of BFT-SMART (based on version 1.1-beta) that implements the optimizations and vote assignment schemes described in [1]. It can be lanuched in the same way as the standard library. 

Example (for the counter demo):

$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterServer 0
$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterServer 1
$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterServer 2
$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterServer 3
$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterServer 4

$ java -cp ./bin/*:./lib/* bftsmart.demo.counter.CounterClient 1001 10

To configure the vote assigment scheme, it is necessary to:

1) sort the replicas IDs from fastest to slowest in ./config/system.config;
2) set the "useWeights" parameter to "true".

Example for 5 replicas (BFT mode, f = 1, delta = 1):

system.initial.view = 4,0,1,2,3
system.useweights = true

The code will automatically compute the values delta, u and Vmax discussed in [1] during start-up. Bear in mind that this code is highly experimental, and only runned in fault-free executions.

To activate tentative executions:

system.tentative=true

Finally, do not forget to always erase the ./config/currentView file after changing N, F, or the order of replicas. This file stores information about the system view, and if it exists, WHEAT/SMART assumes the data from that file instead of creating it again from the settings available in the configuration files.

Feel free to contact us if you have any questions!

References:
[1] J. Sousa and A. Bessani // Separating the WHEAT from the Chaff: An Empirical Design for Geo-Replicated State Machines // SRDS'15

