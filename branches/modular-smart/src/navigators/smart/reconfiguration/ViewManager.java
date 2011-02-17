/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import navigators.smart.reconfiguration.util.TOMConfiguration;



/**
 *
 * @author eduardo
 */
public class ViewManager {
 
    protected View currentView;
    
    protected View initialView;
    
    private TOMConfiguration initialConf;
    
    public ViewManager(int procId){
        this.initialConf = new TOMConfiguration(procId);
        initialView = new View(0,initialConf.getInitialView(),initialConf.getF());
        reconfigureTo(initialView);
    }
        
    public ViewManager(int procId,String configHome){
        this.initialConf = new TOMConfiguration(procId,configHome);
        initialView = new View(0,initialConf.getInitialView(),initialConf.getF());
        reconfigureTo(initialView);
    }
    
    
      
  /* public void reconfigureTo(byte[] reply, boolean isAView){
          ByteArrayInputStream bInp = new ByteArrayInputStream(reply);
        try {
            ObjectInputStream obInp = new ObjectInputStream(bInp);

            if(isAView){
                reconfigureTo((View)obInp.readObject());
            }else{
               reconfigureTo(((ReconfigureReply)obInp.readObject()).getResult());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
   }*/
    
    
   
   
   
    public void reconfigureTo(View newView){
        this.currentView = newView;
    }
    
    public TOMConfiguration getStaticConf() {
        return initialConf;
    }
    
    public boolean isCurrentViewMember(int id){
        return currentView.isMember(id);
    }
    
    public int getCurrentViewId() {
        return currentView.getId();
    }
    
    public int getCurrentViewF() {
        return currentView.getF();
    }
    
    public int getCurrentViewN() {
        return currentView.getN();
    }
    
    public int getCurrentViewPos(int id) {
        return currentView.getPos(id);
    }
    
    public int[] getCurrentViewProcesses() {
        return currentView.getProcesses();
    }
}
