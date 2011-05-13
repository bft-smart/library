/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import java.io.Serializable;
import java.util.Arrays;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author eduardo
 */
public class View implements Serializable {

	private int id;
 	private int f;
 	private int[] processes;
 	private Map<Integer,InetSocketAddress> addresses;

 	public View(int id, int[] processes, int f, InetSocketAddress[] addresses){
 		this.id = id;
 		this.processes = processes;
 		this.addresses = new HashMap<Integer, InetSocketAddress>();

 		for(int i = 0; i < this.processes.length;i++)
 			this.addresses.put(processes[i],addresses[i]);
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
 			ret = ret+processes[i]+"("+addresses.get(processes[i])+"),";
 		}

 		return ret;
 	}
 	public InetSocketAddress getAddress(int id) {
 		return addresses.get(id);
 	}
    
}
