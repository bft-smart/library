/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.demo.simplekv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.core.messages.TOMMessageType;

/**
 *
 * @author alyssonbessani
 */
public class KVClient {
    
    public static void main(String[] args) throws Exception {
        ServiceProxy service = new ServiceProxy(new Integer(args[0]));
        String[] cmds;
        do {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in));
            System.out.print("Print a command:");
            cmds = reader.readLine().split(" ");
            
            if(!cmds[0].isEmpty()) {
                String type = cmds[0].toUpperCase();
                String key = (cmds.length > 1)? cmds[1]: "NOTHING";
                String value = (cmds.length > 2)? cmds[2]: "NOTHING";

                try {
                    KVMessage request = new KVMessage(KVMessage.Type.valueOf(type), key, value);
                    
                    byte[] rep = service.invokeOrdered(request.getBytes());
                    if (rep != null) {
                        KVMessage reply = KVMessage.createFromBytes(rep);
                        System.out.println(type + " operation result: " + reply.key + "," + reply.value);
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        } while(!cmds[0].isEmpty());
        System.exit(0);
    } 
}
