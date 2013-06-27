package bftsmart.statemanagement.strategy.durability;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

import bftsmart.statemanagement.ApplicationState;

public class StateSender implements Runnable {

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
			System.out.print("--- Sending state in different socket");
			oos.writeObject(state);
			System.out.print("--- Sent state in different socket");
			oos.close();
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
