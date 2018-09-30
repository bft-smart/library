/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.reconfiguration.util;

import bftsmart.reconfiguration.VMServices;

/**
 *
 * @author joao
 */
public class DefaultVMServices extends VMServices {
    
    public static void main(String[] args) throws InterruptedException {

        if(args.length == 1){
            System.out.println("####Tpp Service[Disjoint]####");

            int smartId = Integer.parseInt(args[0]);
            
            (new DefaultVMServices()).removeServer(smartId);
            
                
        }else if(args.length == 3){
            System.out.println("####Tpp Service[Join]####");

            int smartId = Integer.parseInt(args[0]);
            String ipAddress = args[1];
            int port = Integer.parseInt(args[2]);

            (new DefaultVMServices()).addServer(smartId, ipAddress, port);

        }else{
            System.out.println("Usage: java -jar TppServices <smart id> [ip address] [port]");
            System.exit(1);
        }

        Thread.sleep(2000);//2s
        

        System.exit(0);
    }
}
