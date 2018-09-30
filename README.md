# Byzantine Fault-Tolerant (BFT) State Machine Replication (SMaRt) v1.2

This is a Byzantine fault-tolerant state machine replication project named BFT-SMaRt, a Java open source library maintained by the LaSIGE research unit at the University of Lisbon.

This package contains the source code (src/), jar file (bin/BFT-SMaRt.jar), dependencies (lib/), documentation (doc/), running scripts (runscripts/), and configuration files (config/) for version 1.2 of the project.
BFT-SMaRt requires the Java Runtime Environment version 1.8 or later.

## Quick start

To run any demonstration you first need to configure BFT-SMaRt to define the protocol behavior and the location of each replica.

The servers must be specified in the configuration file (see `config/hosts.config`):

```
#server id, address and port (the ids from 0 to n-1 are the service replicas) 
0 127.0.0.1 11000
1 127.0.0.1 11010
2 127.0.0.1 11020
3 127.0.0.1 11030
```

**Important tip #1:** Always provide IP addresses instead of hostnames. If a machine running a replica is not correctly configured, BFT-SMaRt may fail to bind to the appropriate IP address and use the loopback address instead (127.0.0.1). This phenomenom may prevent clients and/or replicas from successfully establishing a connection among them.

**Important tip #2:** If some (or all) replicas are deployed/executed within the same machine (127.0.0.1), 'config/hosts.config' cannot have sequencial port numbers (e.g., 10000, 10001, 10002, 10003). This is because each replica binds to 2 ports: one to receive messages from clients (that are configured in 'config/hosts.config', as shown above) and other to receive message from the other replicas (chosen by getting the next port number). More generally, if replica `r` is assigned port number `p`, it will try to bind ports `p` (to received client requests) and `p+1` (to communicate with other replicas). If this guideline is not enforced, replicas may not be able to bind all ports that are needed.

**Important tip #3:** Clients requests should not be issued before all replicas have been properly initialized. Replicas are ready to process client requests when each one outputs `-- Ready to process operations` in the console.

The system configurations also have to be specified (see`config/system.config`). Most of the parameters are self explanatory.

**Important tip #4:** When using the library in real systems, always make sure to set `system.communication.defaultkeys` to `false` and `system.communication.useSignatures` to `1`. Also make sure that only the `config/keys` directory only has the private key for the repective replica/client.

You can run the counter demonstration by executing the following commands, from within the main directory across four different consoles (4 replicas, to tolerate 1 fault):

```
./runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 0
./runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 1
./runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 2
./runscripts/smartrun.sh bftsmart.demo.counter.CounterServer 3
```

**Important tip #5:** If you are getting timeout messages, it is possible that the application you are running takes too long to process the requests or the network delay is too high and PROPOSE messages from the leader does not arrive in time, so replicas may start the leader change protocol. To prevent that, try to increase the `system.totalordermulticast.timeout` parameter in 'config/system.config'.

**Important tip #6:** Never forget to delete the `config/currentView` file after you modify `config/hosts.config` or `config/system.config`. If `config/currentView` exists, BFT-SMaRt always fetches the group configuration from this file first. Otherwise, BFT-SMaRt fetches information from the other files and creates `config/currentView` from scratch. Note that `config/currentView` only stores information related to the group of replicas. You do not need to delete this file if, for instance, you want to enable the debugger or change the value of the request timeout.

Once all replicas are ready, the client can be launched as follows:

```
./runscripts/smartrun.sh bftsmart.demo.counter.CounterClient 1001 <increment> [<number of operations>]
```

If `<increment>` equals 0 the request will be read-only. Default `<number of operations>` equals 1000.

**Important tip #7:** always make sure that each client uses a unique ID. Otherwise, clients may not be able to complete their operations.
  
## State transfer protocol(s)

BFT-SMaRt offers two state transfer protocols. The first is a basic protocol that can be used by extending the classes `bftsmart.tom.server.defaultservices.DefaultRecoverable` and `bftsmart.tom.server.defaultservices.DefaultSingleRecoverable`. Thee classes logs requests into memory and periodically takes snapshots of the application state.

The second, more advanced protocol can be used by extending the class 
`bftsmart.tom.server.defaultservices.durability.DurabilityCoordinator`. This protocol stores its logs to disk. To mitigate the latency of writing to disk, such tasks is done in batches and in parallel with the requests' execution. Additionally, the snapshots are taken at different points of the execution in different replicas.

**Important tip #8:** We recommend developers to use `bftsmart.tom.server.defaultservices.DefaultRecoverable`, since it is the most stable of the three classes.

**Important tip #9:** regardless of the chosen protocol, developers must avoid using Java API objects like `HashSet` or `HashMap`, and use `TreeSet` or `TreeMap` instead. This is because serialization of Hash* objects is not deterministic, i.e, it generates different byte arrays for equal objects. This will lead to problems after more than `f` replicas used the state transfer protocol to recover from failures.

## Group reconfiguration

The library also implements a reconfiguration protocol that can be used to add/remove replicas from the initial group. You can add/remove replicas on-the-fly by executing the following commands:

```
./runscripts/smartrun.sh bftsmart.reconfiguration.util.DefaultVMServices <smart id> <ip address> <port> (to add a replica to the group)
./runscripts/smartrun.sh bftsmart.reconfiguration.util.DefaultVMServices <smart id> (to remove a replica from the group)
```

**Important tip #10:** everytime you use the reconfiguration protocol, you must make sure that all replicas and the host where you invoke the above commands have the latest `config/currentView` file. The current implementation of BFT-SMaRt does not provide any mechanism to distribute this file, so you will need to distribute it on your own (e.g., using the `scp` command). You also need to make sure that any client that starts executing can read from the latest `config/currentView` file.

## BFT-SMaRt under crash faults

You can run BFT-SMaRt in crash-faults only mode by setting the `system.bft` parameter in the configuration file to `false`. This mode requires less replicas to execute, but will not withstand full Byzantine behavior from compromised replicas.

## Generating public/private key pairs

If you need to generate public/private keys for more replicas or clients, you can use the following command:

```
./runscripts/smartrun.sh bftsmart.tom.util.RSAKeyPairGenerator <id> <key size>
```

Keys are stored in the `config/keys` folder. The command above creates key pairs both for clients and replicas. Alternatively, you can set the `system.communication.defaultkeys` to `true` in the `config/system.config` file to forces all processes to use the same public/private keys pair and secret key. This is useful when deploying experiments and benchmarks, because it enables the programmer to avoid generating keys for all principals involved in the system. However, this must not be used in a real deployments.

## Compiling

Make sure that you have Ant installed and simply type `ant` in the main directory. The jar file is stored in the `bin/` directory.

## Additional information and publications

If you are interested in learning more about BFT-SMaRt, you can read:

- The paper about its state machine protocol published in [EDCC'12](http://www.di.fc.ul.pt/~bessani/publications/edcc12-modsmart.pdf):
- The paper about its advanced state transfer protocol published in [Usenix'13](http://www.di.fc.ul.pt/~bessani/publications/usenix13-dsmr.pdf):
- The tool description published in [DSN'14](http://www.di.fc.ul.pt/~bessani/publications/dsn14-bftsmart.pdf):

***Feel free to contact us if you have any questions!***
