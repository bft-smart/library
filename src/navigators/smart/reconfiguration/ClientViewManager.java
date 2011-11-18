/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.reconfiguration;



/**
 *
 * @author eduardo
 */
public class ClientViewManager extends ViewManager{



    public ClientViewManager(int procId) {
        super(procId);
    }

    
    public ClientViewManager(int procId, String configHome) {
        super(procId, configHome);
    }

    public void updateCurrentViewFromRepository(){
         this.currentView = getViewStore().readView();
    }

}