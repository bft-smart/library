# Introduction #

BFT-SMaRt provides interfaces and methods to be called and implemented by client and application code.
Clients can send messages to the server in different manners: ordered, unordered and asynchronous. It can also customize the reply analysis and how message returned from the server can be extracted.
In the server, BFT-SMaRt replicas return the messages from the clients to the application. The application can define how to receive and execute the messages implementing the interfaces provided. It can also choose how to manage the state transfer and even reply messages to different receivers.



# Client interfaces #

`navigators.smart.tom.ServiceProxy` contains the methods for which the client can send messages to the replicas and receive replies.

Client can send messages using three different methods:
  * `invokeOrdered(byte[] request)` - Send message to replicas using a total order multicast protocol. After compare the replies from replicas and at least a number equals to the quorum of matching replies is received, the client receives the return from the method. Until then, it is unable to send requests.
  * `invokeUnordered(byte[] request)` - Send message to all replicas but messages are not ordered among other client requests. Replies are compared and client is blocked to send new requests until reply is received.
  * `invokeAsynschronous(byte[] request, ReplyListener listener, int[] replicas)` - Send message to replicas defined by the replicas parameter. Messages are asynchronous, so after sending a request the client is able to send another request without wait for reply. The `ReplyListener`} interface has to be implemented by the client and defines how the client will manage the reply. It can expect for different replies than the quorum number, compare replies in different manner or define other custom behaviors.
  * `invokeUnorderedHashed(byte[] request)` - Send message to all replicas but messages are not ordered among other client requests. Only one replica, which is randomly chosen, sends a reply, the remaining replicas send a message digest of the reply. The client is blocked to send new requests until the reply is received and the message digests correspond to the reply. Otherwise, an ordered request is issued internally using the invokeOrdered method.

ServiceProxy provides a constructor with two additional parameter, Comparator and Extractor. It provides a basic implementation of Comparator and Extractor. Both can be implemented by the client to have custom behavior.

ReplyListener can be implemented and passed as parameter for invokeAsynchronous calls. It can be customized to define how many messages the client expect from the replicas, which replicas it expecting replies, timeouts and other behaviors.

# Server interfaces #

The application code, running on the server instantiates
> `navigators.smart.tom.ServiceReplica` to receive and process requests.
ServiceReplica provides four constructors to be instantiated using application specific implementations.
The constructors has as parameter the interfaces implemented by the application code. The interfaces available to be implemented are:
    * Executable - Executable allows the application to decide how to execute the received message. Messages can be delivered to the application one by one, ordered or unordered and in batch.
    * Recoverable - Defines how to perform the state transfer. The application can serialize the state as if finds more suitable. It can use Java serialization, or defines its own strategy.
    * Replier - Application can decide how to reply to the requests received. It can reply to the original sender or reply to another client, for instance.

Two additional classes, MessageContext and ReplicaContext are provided for the application to gather information about messages and replicas.