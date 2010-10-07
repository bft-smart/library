/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import java.io.Serializable;
import java.util.Arrays;

/**
 *
 * @author eduardo
 */
public class View implements Serializable {

    private int id;
    private int f;
    private int[] processes;
    
    
    
    
    public View(int id, int[] processes, int f){
        this.id = id;
        this.processes = processes;
        Arrays.sort(this.processes);
        this.f = f;
    }

    public boolean isMember(int id){
        for(int i = 0; i < this.processes.length;i++){
            if(this.processes[i] == id){
                return true;
            }
        }
        return false;
    }
    
    
    public int getPos(int id){
        for(int i = 0; i < this.processes.length;i++){
            if(this.processes[i] == id){
                return i;
            }
        }
        return -1;
    }
    
    public int getId() {
        return id;
    }

    public int getF() {
        return f;
    }
    
    public int getN(){
        return this.processes.length;
    }
    
    public int[] getProcesses() {
        return processes;
    }
    
    @Override
    public String toString(){
        String ret = "ID:"+id+"; F:"+f+"; Processes:";
        for(int i = 0; i < processes.length;i++){
            ret = ret+processes[i]+",";
        }
        
        
        return ret;
    }
    
}
