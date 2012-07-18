package bftsmart;

import bftsmart.reconfiguration.StatusReply;
import bftsmart.reconfiguration.TTP;

public class ReplicaStatusLogger extends Thread {
	
	private int replicaId;
	
	public ReplicaStatusLogger(int id) {
		this.replicaId = id;
	}
	
	public void run() {
		TTP ttp = new TTP();
		while(true) {
			StatusReply reply = ttp.askStatus(replicaId);
			System.out.println("---- Status atual: " + reply);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
