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

import java.net.SocketAddress;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.DefaultViewStorage;
import bftsmart.reconfiguration.views.View;
import bftsmart.reconfiguration.views.ViewStorage;

/**
 *
 * @author eduardo
 */
public class ViewController {

    protected View lastView = null;
    protected View currentView = null;
    private TOMConfiguration staticConf;
    private ViewStorage viewStore;
    private String configHome = "";

    public ViewController(int procId) {
        this.staticConf = new TOMConfiguration(procId);
    }

    public ViewController(int procId, String configHome) {
        this.staticConf = new TOMConfiguration(procId, configHome);
        System.out.println("Current config home: " + this.configHome + "next config home" + configHome);
        this.configHome = configHome;
    }
    public final ViewStorage getViewStore() {
        if(this.viewStore == null) {
            System.out.println("viewStore is null? " + viewStore == null
                    + " configHome.isEmpty? " + configHome.isEmpty()
                    + " lastView: " + lastView
                    + " currentView: " + currentView);
            System.out.println("Config home in getViewStore: " + this.configHome);
            if(this.configHome.isEmpty()) {
                this.viewStore = new DefaultViewStorage();
            } else {
                this.viewStore = new DefaultViewStorage(this.configHome);
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
    
    public View getLastView(){
        return this.lastView;
    }
    
    public SocketAddress getRemoteAddress(int id) {
        return getCurrentView().getAddress(id);
    }
    
    public void reconfigureTo(View newView) {
        this.lastView = this.currentView;
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