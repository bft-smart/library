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
    }

    
    public ViewManager(int procId, String configHome) {
        this.staticConf = new TOMConfiguration(procId, configHome);
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
    
    public SocketAddress getRemoteAddress(int id) {
        return getCurrentView().getAddress(id);
    }
    
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