/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.core.messages.TOMMessageType;
import navigators.smart.tom.util.TOMUtil;

/**
 *
 * @author eduardo
 */
public class Reconfiguration {

    private ReconfigureRequest request;
    private ServiceProxy proxy;
    
    
    public Reconfiguration(int id) {
        proxy = new ServiceProxy(id);
        
        request = new ReconfigureRequest(id);
    }
    
    public void addServer(int id, String ip, int port){
        this.setReconfiguration(ReconfigurationManager.ADD_SERVER, id + ":" + ip + ":" + port);
    }
    
    public void removeServer(int id){
        this.setReconfiguration(ReconfigurationManager.REMOVE_SERVER, String.valueOf(id));
    }
    

    public void setF(int f){
      this.setReconfiguration(ReconfigurationManager.CHANGE_F,String.valueOf(f));  
    }
    
    
    public void setReconfiguration(int prop, String value){
        if(request == null){
            request = new ReconfigureRequest(proxy.getViewManager().getStaticConf().getProcessId());
        }
        request.setProperty(prop, value);
    }
    
    public ReconfigureReply execute(){
        
        
        
        byte[] signature = TOMUtil.signMessage(proxy.getViewManager().getStaticConf().getRSAPrivateKey(),
                                                                            request.toString().getBytes());
        request.setSignature(signature);
        byte[] reply = proxy.invoke(TOMUtil.getBytes(request), TOMMessageType.RECONFIG);
        request = null;
        return (ReconfigureReply)TOMUtil.getObject(reply);
   }
    
    public void close(){
        proxy.close();
    }
    
}
