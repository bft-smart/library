package bftsmart.statemanagement.strategy.durability;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.defaultservices.durability.DurabilityCoordinator;

public class StateSenderServer implements Runnable {

	private ServerSocket server;
	private ApplicationState state;
	private Recoverable recoverable;
	private DurabilityCoordinator coordinator;
	private CSTRequest request;
	
	public void setState(ApplicationState state) {
		this.state = state;
	}
	
	public void setRecoverable(Recoverable recoverable) {
		this.recoverable = recoverable;
		coordinator = (DurabilityCoordinator)(recoverable);
	}
	
	public void setRequest(CSTRequest request) {
		this.request = request;
	}

	public StateSenderServer(int port) {
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			Socket socket = server.accept();
			StateSender sender = new StateSender(socket);
			state = coordinator.getState(request);
			sender.setState(state);
			new Thread(sender).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
