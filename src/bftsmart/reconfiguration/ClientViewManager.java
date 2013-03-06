/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.reconfiguration;

import java.net.InetSocketAddress;
import bftsmart.reconfiguration.views.View;

/**
 *
 * @author eduardo
 */
public class ClientViewManager extends ViewManager{

    public ClientViewManager(int procId) {
        super(procId);
        View cv = getViewStore().readView();
        if(cv == null){
            reconfigureTo(new View(0, getStaticConf().getInitialView(), 
                getStaticConf().getF(), getInitAdddresses()));
        }else{
            reconfigureTo(cv);
        }
    }

    public ClientViewManager(int procId, String configHome) {
        super(procId, configHome);
        View cv = getViewStore().readView();
        if(cv == null){
            reconfigureTo(new View(0, getStaticConf().getInitialView(), 
                getStaticConf().getF(), getInitAdddresses()));
        }else{
            reconfigureTo(cv);
        }
    }

    public void updateCurrentViewFromRepository(){
         this.currentView = getViewStore().readView();
    }
    
    private InetSocketAddress[] getInitAdddresses() {
        int nextV[] = getStaticConf().getInitialView();
        InetSocketAddress[] addresses = new InetSocketAddress[nextV.length];
        for (int i = 0; i < nextV.length; i++) {
            addresses[i] = getStaticConf().getRemoteAddress(nextV[i]);
        }

        return addresses;
    }
}