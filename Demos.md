# BFT-SMaRt demos #

There are several demo applications deployed together with the BFT-SMaRt source code.
These demos are in the package `bftsmart.demo`.

BFT-SMaRt has a configuration file with options to run the protocol. The configuration file is in config/system.config. A short description is provided before each parameter, and values are set for the most common operations.

Before running the protocol, the configuration file config/hosts.config must be updated with the information about the servers where the protocol is being executed. The file is in the format:

#server id, address and port (the ids from 0 to n-1 are the service replicas)<br />
0 localhost 11234<br />
1 localhost 11235<br />
2 localhost 11236<br />
3 localhost 11237<br />

There is a script created to setup the packages and required libraries. This script is "runsmart.bat" in Windows and "runsmart.sh" script em UNIX-like systems (Linux, MacOS, etc.). These scripts can easily be adapted to run other demos or use BFT-SMaRt in other operating systems. It is important to run the scripts from the BFT-SMaRt root folder, as it is considered the base do find the libraries.

The client\_id parameter accepted in BFT-SMaRt clients is used as an unique identifier of the client. It is essential for the protocol to order the messages consistently across the replicas. If the protocol is using client signatures, a key with the clients and servers keys must be provided in the config/keys. For testing purposes, several keys are delivered with the source code.
The parameter system.communication.useSignatures in system.config defines if signatures will be considered or not.

## CounterServer ##

CounterServer is an application where clients submits messages to replica, messages are ordered and the throughput is displayed to the user. It has no real application but is useful to verify the system functioning and performance.

### Start the servers (4 replicas, to tolerate 1 fault) ###

`runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 0`<br />
`runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 1`<br />
`runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 2`<br />
`runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 3`<br />

The only parameter is the replica id that must be assigned sequentially starting on 0.

### Start a client ###

#if `<increment>` equals 0 the request will be read-only
#default `<number of operations>` equals 1000

`runscripts/smartrun.sh bftsmart.demo.counter.CounterClient <client_id> <increment> [<number of operations>]`

## BFTMap ##

BFTMap is a table of tables simulating a database.
It creates a HashMap in the server and the replicas maintain the state of the table with the operations executed by the clients.

### Start the servers (4 replicas, to tolerate 1 fault) ###

`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapServer 0`<br />
`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapServer 1`<br />
`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapServer 2`<br />
`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapServer 3`<br />

The only parameter is the replica id that must be assigned sequentially starting on 0.

### Start a client ###

`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapClient <client_id>`

This client will send repeated keys to the server until the process is killed.
It is useful when several different clients are created, so it is possible to verify the ordering and concurrent execution of the messages.

Another client for the BFTMap demo is BFTMapInteractiveClient. That client works accepting inputs from the user to create tables and insert values in the server.

The command line to start the client is:

`runscripts/smartrun.sh bftsmart.demo.bftmap.BFTMapInteractiveClient <client_id>`

After start the client, instructions will be displayed on how to input values.