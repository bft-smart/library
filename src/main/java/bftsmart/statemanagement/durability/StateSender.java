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
package bftsmart.statemanagement.durability;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

import bftsmart.statemanagement.ApplicationState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateSender implements Runnable {

        private Logger logger = LoggerFactory.getLogger(this.getClass());
    
	private final Socket socket;
	private ApplicationState state;
	
	public StateSender(Socket socket) {
		this.socket = socket;
	}
	
	public void setState(ApplicationState state) {
		this.state = state;
	}
	
	@Override
	public void run() {
		try {
			OutputStream os = socket.getOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(os);
			logger.debug("Sending state in different socket");
			oos.writeObject(state);
			logger.debug("Sent state in different socket");
			oos.close();
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error("Could not send state",e);
		}
	}

}
