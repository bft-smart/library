/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.reconfiguration.views;

/**
 *
 * @author eduardo
 */
public interface ViewStorage {
    
    public boolean storeView(View view);
    public View readView();
    
}
