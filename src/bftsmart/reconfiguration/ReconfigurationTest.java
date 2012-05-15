/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package bftsmart.reconfiguration;

import bftsmart.reconfiguration.views.View;

/**
 *
 * @author eduardo
 */
public class ReconfigurationTest {

    public ReconfigurationTest() {
    }

    public void run(int id){
       /* ServiceProxy proxy = new ServiceProxy(id);
        
        ReconfigureRequest request = new ReconfigureRequest(id);
        request.setProperty("f","1");
        
        System.out.println("Going to send a reconf!!!");
        
        byte[] reply = proxy.invoke(TOMUtil.getBytes(request), ReconfigurationManager.TOM_RECONFIG_REQUEST, false);
        
        ReconfigureReply r = (ReconfigureReply)TOMUtil.getObject(reply);*/
        
        Reconfiguration rec = new Reconfiguration(id);
        
        //rec.setReconfiguration(ReconfigurationManager.CHANGE_F,"1");
        rec.setF(2);
        
        ReconfigureReply r = rec.execute();
        
        
        
        View v = r.getView();
        
        System.out.println("New view f: "+v.getF());
        
        rec.close();
   }
    
    
    
    public static void main(String[] args){
        new ReconfigurationTest().run(Integer.parseInt(args[0]));
    }
    
    
    
    
}
