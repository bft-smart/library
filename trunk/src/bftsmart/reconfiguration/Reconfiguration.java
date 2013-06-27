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
package bftsmart.reconfiguration;

import java.security.PrivateKey;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.TOMUtil;

/**
 *
 * @author eduardo
 */
public class Reconfiguration {

    private ReconfigureRequest request;
    private ServiceProxy proxy;
    private int id;
    
    public Reconfiguration(int id) {
        this.id = id;
         //proxy = new ServiceProxy(id);
        //request = new ReconfigureRequest(id);
    }
    
    public void connect(){
        if(proxy == null){
            proxy = new ServiceProxy(id);
        }
    }
    
    public void addServer(int id, String ip, int port){
        this.setReconfiguration(ServerViewManager.ADD_SERVER, id + ":" + ip + ":" + port);
    }
    
    public void removeServer(int id){
        this.setReconfiguration(ServerViewManager.REMOVE_SERVER, String.valueOf(id));
    }
    

    public void setF(int f){
      this.setReconfiguration(ServerViewManager.CHANGE_F,String.valueOf(f));  
    }
    
    
    public void setReconfiguration(int prop, String value){
        if(request == null){
            //request = new ReconfigureRequest(proxy.getViewManager().getStaticConf().getProcessId());
            request = new ReconfigureRequest(id);
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
    
    
    protected StatusReply askStatus(int id) {
        request = new ReconfigureRequest(id);
        PrivateKey key = proxy.getViewManager().getStaticConf().getRSAPrivateKey();
        byte[] reqBytes = request.toString().getBytes(); 
        byte[] signature = TOMUtil.signMessage(key, reqBytes);
		request.setSignature(signature);
		StatusReplyListener listener = new StatusReplyListener();
		proxy.invokeAsynchronous(TOMUtil.getBytes(request), listener, new int[] {id}, TOMMessageType.ASK_STATUS);
		StatusReply status = listener.getResponse();
		return status;
    }
    
    public void close(){
        proxy.close();
        proxy = null;
    }
    
}
