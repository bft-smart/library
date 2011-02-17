/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo;

/**
 *
 * @author sweta
 */
public class KVServer {

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java KVServer <processId>");
            System.exit(-1);
        }

    BFTMapImpl bftServer = new BFTMapImpl(Integer.parseInt(args[0]));
    
    }

}
