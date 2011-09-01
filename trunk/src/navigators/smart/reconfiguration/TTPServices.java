/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

/**
 *
 * @author Andre Nogueira
 */

public class TTPServices {
	public static void main(String[] args) throws InterruptedException {

		TTP ttp = new TTP();


		if(args.length == 1){
			System.out.println("####Tpp Service[Disjoint]####");

			int smartId = Integer.parseInt(args[0]);

			ttp.removeServer(smartId);

		}else if(args.length == 3){
			System.out.println("####Tpp Service[Join]####");

			int smartId = Integer.parseInt(args[0]);
			String ipAddress = args[1];
			int port = Integer.parseInt(args[2]);

			ttp.addServer(smartId, ipAddress,port);

		}else{
			System.out.println("Usage: java -jar TppServices <smart id> [ip address] [port]");
			System.exit(1);
		}

		ttp.executeUpdates();

                Thread.sleep(2000);

		ttp.close();

		System.exit(0);
	}
}
