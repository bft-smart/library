# Getting Started with BFT-SMaRt #

BFT-SMaRt provides a middleware to replicate request from multiple clients to multiple servers. This page has the instructions to download and install BFT-SMaRt.

## Download ##

First, download the latest stable version of BFT-SMaRt from this [link](https://drive.google.com/folderview?id=0B6fb-GjKp57SbHd3ZnZPeGpiVEU&usp=sharing) or from the [repository](http://code.google.com/p/bft-smart/source/checkout).

## Installation ##

The BFT-SMaRt code has to be installed in each replica and client that will use it.
To do so, first, extract the archive downloaded. After that, copy the following files and folders to each of the servers and clients:

- bin/BFT-SMaRt.jar<br />
- config/<br />
- runscripts/<br />
- lib/

After copying the files to the servers, the first step is to get the ip addresses from each server and define a port for each one to receive the messages from other servers. After that, edit the file hosts.config in each replica to set the ip address and port for each server. The information must be the same in all replicas.
Let's use as example this configuration:

0 127.0.0.1 10001<br />
1 127.0.0.2 10001<br />
2 127.0.0.3 10001<br />
3 127.0.0.4 10001

For each line, the first parameter is the replica id, that is the parameter used when starting a replica. We will get to that later. The second parameter is the ip address and the third is the port. This information should be the same in all servers.

By now you should be able to start replicas and run demo examples that come together with the BFT-SMaRt distribution. Instructions to run the demos deployed with BFT-SMaRt, please see the [Demos](http://code.google.com/p/bft-smart/wiki/Demos) wiki.

Next, we describe how to extend BFT-SMaRt to build your own application.

We will use as an example a implementation of the `java.util.Map` interface. In this example, multiple clients will be able to perform operations in a HashMap and the data will be replicated in several replicas.

## Client code ##

First, lets start by creating the client implementation of the Map interface:

```
package foo.gettingstarted.client;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class HashMapClient implements Map<String, String> {
...
```

The interface `java.util.Map` defines several methods to be implemented. The objective  of this example is to show how to extend BFT-SMaRt to do your own application, so, we will only implement the methods `put(String, String)`, `get(Object)`, `remove(Object)` and `size()`.

The client `HashMap` we are creating will interact with BFT-SMaRt through the class `bftsmart.tom.ServiceProxy`, so we will declare a `ServiceProxy` object to be used by the client. We will also create a constructor for the client passing the client id as a parameter, so the requests performed by this client will be identified with this id.

```
	ServiceProxy clientProxy = null;

	
	public HashMapClient(int clientId) {
		clientProxy = new ServiceProxy(clientId);
	}
```

To make the code clear we will create a class to indicate the request type.

```
public class RequestType {
	static final int PUT = 1;
	static final int GET = 2;
	static final int REMOVE = 3;
	static final int SIZE = 4;
}
```

After that, we will implement the `put` method.

We are using `ByteArrayOutputStream` and `DataOutputStream` to convert the data we have to send to byte arrays as the `ServiceProxy.invokeOrdered` method takes a byte array as parameter. Also, we are calling the invoke ordered method because put is a write call, so it has to be synchronized across all clients to guaranty that all replicas will write the same sequence of values and all clients will see the same table.

```
	@Override
	public String put(String key, String value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(out);
		try {
			dos.writeInt(RequestType.PUT);
			dos.writeUTF(key);
			dos.writeUTF(value);
			byte[] reply = clientProxy.invokeOrdered(out.toByteArray());
			if(reply != null) {
				String previousValue = new String(reply);
				return previousValue;
			}
			return null;
		} catch(IOException ioe) {
			System.out.println("Exception putting value into hashmap: " + ioe.getMessage());
			return null;
		}
	}
```

The `get` method is close to the `put` method. The difference is that in this method we will call the method `ServiceProxy.invokeUnordered` to read the data. The `invokeUnordered` method will not call the consensus protocol of BFT-SMaRt to order the requests received from the clients because it is a read request that will not change the state of the application.

```
	@Override
	public String get(Object key) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out);
			dos.writeInt(RequestType.GET);
			dos.writeUTF(String.valueOf(key));
			byte[] reply = clientProxy.invokeUnordered(out.toByteArray());
			String value = new String(reply);
			return value;
		} catch(IOException ioe) {
			System.out.println("Exception getting value from the hashmap: " + ioe.getMessage());
			return null;
		}
	}
```

The `remove` method will look like the `put` method as remove a value is also a write operation.

```
	@Override
	public String remove(Object key) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(out);
		try {
			dos.writeInt(RequestType.REMOVE);
			dos.writeUTF(String.valueOf(key));
			byte[] reply = clientProxy.invokeOrdered(out.toByteArray());
			if(reply != null) {
				String removedValue = new String(reply);
				return removedValue;
			}
			return null;
		} catch(IOException ioe) {
			System.out.println("Exception removing value from the hashmap: " + ioe.getMessage());
			return null;
		}
	}

```

The `size` method is almost the same as `get`, with the difference that it will not pass a parameter to be read. It will only indicate the operation and will invoke it unordered.

```
	@Override
	public int size() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(out);
			dos.writeInt(RequestType.SIZE);
			byte[] reply = clientProxy.invokeUnordered(out.toByteArray());
			ByteArrayInputStream in = new ByteArrayInputStream(reply);
			DataInputStream dis = new DataInputStream(in);
			int size = dis.readInt();
			return size;
		} catch(IOException ioe) {
			System.out.println("Exception getting the size the hashmap: " + ioe.getMessage());
			return -1;
		}
	}
```

## Server code ##

To perform the application business on the server, a class must be created to receive the requests from the client and interact with BFT-SMaRt.

BFT-SMaRt requires the server to implement an `Executable` and a
`Recoverable` interface. The `Executable` interface define methods to process ordered and unordered requests. The application on the server must implement it's business logic inside these methods.

The `Recoverable` interface defines methods to manage the state of the application in the presence of faults. It is useful to store the state of the application during it's execution and read the state back in the case of a replica need to receive the state from other replicas due to an outage or to a new replica being added to the system.

In the example we show here, we extend the class `DefaultSingleRecoverable` that will provide basic code to manage the state of the application, so the developer don't have to code it from the scratch. Its use is not required though. Another option is the `DefaultBatchRecoverable` that will do the same as the single recoverable but will extend BatchExecutable that receives batches of requests from BFT-SMaRt instead of a single request per time.

### Request processing ###

```
public class HashMapServer extends DefaultSingleRecoverable {

	ServiceReplica replica = null;
	private ReplicaContext replicaContext;
	Map<String, String> table;
	
	public HashMapServer(int id) {
		replica = new ServiceReplica(id, this, this);
		table = new HashMap<String, String>();
	}

	@Override
	public void setReplicaContext(ReplicaContext replicaContext) {
		this.replicaContext = replicaContext;
	}
```

The first point to note here is that a `ServiceReplica` object is instantiated. This object will communicate with BFT-SMaRt to get requests and return replies.

The `ReplicaContext` object is used by the application to retrieve information about the BFT-SMaRt protocol like the current view, communication system and others.

The `Map<String, String> table` is the actual object in which the data will be stored in the server.

After setting the `ReplicaContext` object, we will create the methods to processes the ordered and unordered request. Lets start by implementing `appExecuteOrdered` to process the ordered requests, to put in or remove values from the map. The original method, defined by the interface `bftsmart.tom.server.SingleExecutable` is called `executeOrdered(byte[] command, MessageContext msgCtx)`. This method in implemented in `DefaultSingleRecoverable` to perform basic actions to manage the state of the application. `DefaultSingleRecoverable` defines the method `appExecuteOrdered(byte[] command, MessageContext msgCtx)` for the application to implement the business logic.

Our implementation of `appExecuteOrdered` will parse the command received to identify the operation. After that it will read the appropriate commands, perform the operations in the map and return the results to the protocol, that will forward it to the client.

```
	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        ByteArrayInputStream in = new ByteArrayInputStream(command);
        DataInputStream dis = new DataInputStream(in);
        int reqType;
		try {
			reqType = dis.readInt();
	        if(reqType == RequestType.PUT) {
	        	String key = dis.readUTF();
	        	String value = dis.readUTF();
	        	String oldValue = table.put(key, value);
	        	byte[] resultBytes = null;
	        	if(oldValue != null)
	        		resultBytes = oldValue.getBytes();
	        	return resultBytes;
	        } else if(reqType == RequestType.REMOVE) {
	        	String key = dis.readUTF();
	        	String removedValue = table.remove(key);
	        	byte[] resultBytes = null;
	        	if(removedValue != null)
	        		resultBytes = removedValue.getBytes();
	        	return resultBytes;
	        } else {
	        	System.out.println("Unknown request type: " + reqType);
	        	return null;
	        }
		} catch (IOException e) {
			System.out.println("Exception reading data in the replica: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
```

After writing the method to perform the ordered operations we will write the method to process the unordered operations, in our case, `get(String key)` and `size()`.

```
	@Override
	public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
		ByteArrayInputStream in = new ByteArrayInputStream(command);
		DataInputStream dis = new DataInputStream(in);
		int reqType;
		try {
			reqType = dis.readInt();
			if(reqType == RequestType.GET) {
				String key = dis.readUTF();
				String readValue = table.get(key);
				byte[] resultBytes = null;
				if(readValue != null)
					resultBytes = readValue.getBytes();
				return resultBytes;
			} else if(reqType == RequestType.SIZE) {
				int size = table.size();
				byte[] sizeInBytes = toBytes(size);
				return sizeInBytes;
			} else {
				System.out.println("Unknown request type: " + reqType);
				return null;
			}
		} catch (IOException e) {
			System.out.println("Exception reading data in the replica: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
```

### State management ###

The management of the application state is important to guarantee availability and durability of the application state despite of failures. During the normal processing of requests, replicas take log of the requests processed and from a pre-defined time take a snapshot of the application state to bound the size of the log. `DefaultSingleRecoverable` provides basic implementation for the Recoverable methods and left for the application developer the task of define a format and a method for how the state is saved and read again.

We implemented `getSnapshot()` and `installSnapshot(byte[])` to respectively save and read back the state. These methods are useful to transfer the state to new replicas, replicas that are to late to process requests or replicas that was disconnected or shutdown and started again.

This implementation is very simple and only saves the state of the application in the memory to be transfered when needed. Other options of implementation could store the state and log in disk or other replicas.

```
	@Override
	public void installSnapshot(byte[] state) {
		ByteArrayInputStream bis = new ByteArrayInputStream(state);
		try {
			ObjectInput in = new ObjectInputStream(bis);
			table = (Map<String, String>)in.readObject();
			in.close();
			bis.close();
		} catch (ClassNotFoundException e) {
			System.out.print("Coudn't find Map: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.print("Exception installing the application state: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public byte[] getSnapshot() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(table);
			out.flush();
			out.close();
			bos.close();
			return bos.toByteArray();
		} catch (IOException e) {
			System.out.println("Exception when trying to take a + " +
					"snapshot of the application state" + e.getMessage());
			e.printStackTrace();
			return new byte[0];
		}
	}
```

The interface `bftsmart.statemanagement.Recoverable` define methods to manage the state of the application. The s


## Test ##

To test what we have done so far, let's write a small console application to perform operations in the classes we created. This example is similar to the demo found in `bftsmart.demo.bftmap.BFTMapInteractiveClient`.

```
package foo.gettingstarted.client;

import java.io.Console;
import java.util.Scanner;

public class ConsoleClient {

	public static void main(String[] args) {
		if(args.length < 1) {
			System.out.println("Usage: ConsoleClient <client id>");
		}
		
		HashMapClient client = new HashMapClient(Integer.parseInt(args[0]));
		Console console = System.console();
		
		Scanner sc = new Scanner(System.in);
		
		while(true) {
			System.out.println("Select an option:");
			System.out.println("1. ADD A KEY AND VALUE TO THE MAP");
			System.out.println("2. READ A VALUE FROM THE MAP");
			System.out.println("3. REMOVE AND ENTRY FROM THE MAP");
			System.out.println("4. GET THE SIZE OF THE MAP");
			
			int cmd = sc.nextInt();
			
			switch(cmd) {
			case 1:
				System.out.println("Putting value in the map");
				String key = console.readLine("Enter the key:");
				String value = console.readLine("Enter the value:");
				String result =  client.put(key, value);
				System.out.println("Previous value: " + result);
				break;
			case 2:
				System.out.println("Reading value from the map");
				key = console.readLine("Enter the key:");
				result =  client.get(key);
				System.out.println("Value read: " + result);
				break;
			case 3:
				System.out.println("Removing value in the map");
				key = console.readLine("Enter the key:");
				result =  client.remove(key);
				System.out.println("Value removed: " + result);
				break;
			case 4:
				System.out.println("Getting the map size");
				int size = client.size();
				System.out.println("Map size: " + size);
				break;
			}
		}
	}
}
```

## Running the test ##

To run BFT-SMaRt and the test we just created it is necessary to copy and reference all the libraries and classes to the java classpath. To demonstrate how to do this, lets assume the creation of a directory called tmp/ as the root of our test.

It is necessary to have in this directory the BFT-SMaRt configuration files, the libraries and the custom application code we developed.

First, lets create a /temp/lib/ directory and copy all libraries from the BFT-SMaRt/lib/ to there. After that, we will create a /tmp/config/ directory and copy system.config and hosts.config from BFT-SMaRt to there.

If you are running the test in multiple machines, please remember to do this process in all replicas and also to set hosts.config up to reflect the correct IPs for the replicas.

Finally, we will copy the classes created for the test. Lets assume we created a library called foo.jar.

Now, assume you are in the root of the test directory, in this case tmp/.

The command to start the replicas will be:

```sh

$ java -cp lib/BFT-SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar:lib/foo.jar foo.gettingstarted.server.HashMapServer 0
```

It is necessary to instantiate 3f+1 or more replicas to perform this test. Assuming that the protocol will tolerate one faulty replica, it is necessary to start four replicas, so, repeat the process above replace the parameter from 0 to 3.

To run the console client we created, the command line code will be:

```sh

$ java -cp lib/BFT-SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar:lib/foo.jar foo.gettingstarted.client.ConsoleClient 1001
```

After that the text interface will be displayed and it will be possible to test hash map operations defined.

## Code ##

The code written for this example can be found [here](http://bft-smart.googlecode.com/svn/example/foo.zip).