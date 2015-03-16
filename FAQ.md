# Frequently Asked Questions #

In this page we list and answer common questions that new users asks when running BFT-SMaRt for the first times.<br />

**1. I have download BFT-SMaRt from the project's page. What's next? How can I use it? Is there an example?**

BFT-SMaRt code comes with several demonstration examples. [This page](http://code.google.com/p/bft-smart/wiki/Demos) has a description of some of these demos and how to run them.<br />

**2. How can I extend BFT-SMaRt to replicate the business logic for my application?**

The class `bftsmart.tom.ServiceReplica` provides the methods for clients to send requests and receive responses from BFT-SMaRt. In the server side, there are many interfaces in the package `bftsmart.tom.server` to be implemented depending on the behavior desired by the user. The main ones are `Executable` and `Recoverable` to implement the processing of requests from the application and state management, respectively.<br />

**3. I try to run BFT-SMaRt but it is not working. I keep receiving messages that it can't connect to replicas, messages about timeout or messages saying that it is forwarding messages to some replica. Am I doing something wrong?**

BFT-SMaRt is a replication protocol comprised of several pieces like state transfer, leader change and reconfiguration. Because of those small details, there is a lot of configuration parameters and options to "tune up" smart to the needs of the application being replicated. However, there are some common practices to make BFT-SMaRt work as aspected for most of the cases.

First, before starting BFT-SMaRt replicas, be sure that the IP addresses of the replicas is correctly configured in `config/hosts.config` in all replicas and that each replica has the port defined accepting messages.

Second, before start BFT-SMaRt replicas for the first time (not when when replica went down and up again), make sure to remove the file `config/currentView`. That file is used to a replica that was shutdown or disconnected and is comming back again, but when starting the application may point the replicas to an incorrect view.

Finally, if you are seeing timeout messages it is possible that the application you are running takes too long to process the requests or the network delay is too high and propose messages from the leader don't arrive in time, so replicas may start the leader change protocol. To test and prevent that, try to increase the timeout parameter in `config/system.config` in `system.totalordermulticast.timeout`.<br />

**4. I kill a replica and when I started it again the system does not perform the state transfer protocol. What is wrong?**

When a replica is started it has no idea that it is delayed in relation to the others until it receives the consensus decision from another replica and sees that it number is bigger than the expected (1 if it is started again or some old value if the replica lost messages). So, to start the state transfer it is necessary to start a replica and make sure that clients are sending requests.<br />

**5. When I kill a replica, recover it and kill another, the protocol stops working. Is it working?**

The state transfer protocol has several steps and takes some time until a replica is considered recovered. In the case above, we consider that the number of faults tolerated is one, so, we can't have any other fault until the replica is recovered. To test it properly, the first replica has to be shut down, started back and the state transfer initiated. The replica will ask the state for the other replicas, receive it, install it and, only after that it will start processing the requests that as added to a queue while the state was being installed. It may take some time to synchronize the processing with the other replicas. Only after the replica is in processing the requests with the others it is considered back, and then, the protocol tolerates fault in another or in the same replica.<br />

**6. I found a bug in BFT-SMaRt. Is there a way to open a ticket or submit a patch proposal?**

You can post your findings in our [forum](mailto:bft-smart@googlegroups.com). We will reply as soon as possible.<br />

**7. Should I start all replicas before accepting client's requests?**

The BFT-SMaRt protocol works with the assumption of 3f-1 replicas, where f is the number of faulty replicas. Considering that, for f equals 1, only three replicas are needed for the protocol to work. For f equals 2, five replicas are needed and so on. Remember that, having only the necessary replicas running, the system won't tolerate any additional fauld, as the replicas not up are considered faulty.

**8. How can I change the leader in BFT-SMaRt?**

The leader of the protocol is not chosen by the client or the anyone that administer the system. The protocol votes for new leader in case of the current one not fulfilling the timeout assumptions defined. It the leader stops behaving as expected, timeouts may occur and replicas noticing that propose messages are taking longer than expected to arrive, will start a leader change protocol.<br />