/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.counter;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Example replica that implements a BFT replicated service (a counter).
 * If the increment > 0 the counter is incremented, otherwise, the counter
 * value is read.
 * 
 * @author alysson
 */

public final class CounterServer extends DefaultSingleRecoverable  {
    
    private int counter = 0;
    private int iterations = 0;
    
    private static final Logger log = LoggerFactory.getLogger(CounterServer.class);
    
    public CounterServer(int id) {
    	new ServiceReplica(id, this, this);
    }
            
    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {         
        iterations++;
        log.debug("({}) Reading counter at value: {} ", iterations, counter);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(counter);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Invalid request received!");
            return new byte[0];
        }
    }
  
    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        iterations++;
        try {
            int increment = new DataInputStream(new ByteArrayInputStream(command)).readInt();
            counter += increment;
            
            log.debug("({}) Counter was incremented. Current value = {}" , iterations , counter);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(counter);
            return out.toByteArray();
        } catch (IOException ex) {
        	log.error("Invalid request received!");
            return new byte[0];
        }
    }

    
    @SuppressWarnings("unchecked")
    @Override
    public void installSnapshot(byte[] state) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(state);
            ObjectInput in = new ObjectInputStream(bis);
            counter = in.readInt();
            in.close();
            bis.close();
        } catch (IOException e) {
            log.error("Error deserializing state: {}", e.getMessage());
        }
    }

    @Override
    public byte[] getSnapshot() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeInt(counter);
            out.flush();
            bos.flush();
            out.close();
            bos.close();
            return bos.toByteArray();
        } catch (IOException ioe) {
        	log.error("Error serializing state. {}", ioe.getMessage());
            return "ERROR".getBytes();
        }
    }
    
    public static void main(String[] args){
        
    	if(args.length != 1) {
            log.info("Usage: java CounterServer <processId>");
            System.exit(-1);
        }      
        new CounterServer(Integer.parseInt(args[0]));
    }

    
}
