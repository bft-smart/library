/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.reconfiguration;

import java.net.SocketAddress;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.DefaultViewStorage;
import bftsmart.reconfiguration.views.View;
import bftsmart.reconfiguration.views.ViewStorage;

/**
 *
 * @author eduardo
 */
public class ViewManager {

    protected View currentView;
    private TOMConfiguration staticConf;
    private ViewStorage viewStore;

    public ViewManager(int procId) {
        this.staticConf = new TOMConfiguration(procId);
        //initialView = new View(0, initialConf.getInitialView(), initialConf.getF(), getAdddresses(initialConf));
        //reconfigureTo(initialView);
        //this.currentView = getViewStore().readView();
    }

    
    public ViewManager(int procId, String configHome) {
        this.staticConf = new TOMConfiguration(procId, configHome);
        //initialView = new View(0, initialConf.getInitialView(), initialConf.getF(), getAdddresses(initialConf));
        //reconfigureTo(initialView);
        //this.currentView = getViewStore().readView();
    }

    
    public final ViewStorage getViewStore() {
        if (this.viewStore == null) {
            String className = staticConf.getViewStoreClass();
            try {
                this.viewStore = (ViewStorage) Class.forName(className).newInstance();
            } catch (Exception e) {
                this.viewStore = new DefaultViewStorage();
            }

        }
        return this.viewStore;
    }

    public View getCurrentView(){
        if(this.currentView == null){
             this.currentView = getViewStore().readView();
        }
        return this.currentView;
    }
    
   
    
    /*private InetSocketAddress[] getAdddresses(TOMConfiguration initialConf) {

        int nextV[] = initialConf.getInitialView();
        InetSocketAddress[] addresses = new InetSocketAddress[nextV.length];
        for (int i = 0; i < nextV.length; i++) {
            addresses[i] = initialConf.getRemoteAddress(nextV[i]);
        }

        return addresses;
    }*/

    public SocketAddress getRemoteAddress(int id) {
        return getCurrentView().getAddress(id);
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
    public void reconfigureTo(View newView) {

        this.currentView = newView;
    }

    public TOMConfiguration getStaticConf() {
        return staticConf;
    }

    public boolean isCurrentViewMember(int id) {
        return getCurrentView().isMember(id);
    }

    public int getCurrentViewId() {
        return getCurrentView().getId();
    }

    public int getCurrentViewF() {
        return getCurrentView().getF();
    }

    public int getCurrentViewN() {
        return getCurrentView().getN();
    }

    public int getCurrentViewPos(int id) {
        return getCurrentView().getPos(id);
    }

    public int[] getCurrentViewProcesses() {
        return getCurrentView().getProcesses();
    }
}