/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.reconfiguration;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.communication.server.ServerConnection;

/**
 *
 * @author eduardo
 */
public class TTP {

    private int id;
    private Reconfiguration rec = null;
    private Hashtable<Integer, ServerConnection> connections = new Hashtable<Integer, ServerConnection>();
    private ReconfigurationManager manager;
    
    //Apenas precisa notificar quem está entrando, pois os que já estão no sistema executarão
    //a requisição de reconfiguração
    private List<Integer> addIds = new LinkedList<Integer>();
    
    public TTP() {
        this("");
    }

    
    
    public TTP(String configHome) {
        this.id = loadID(configHome);
        this.manager = new ReconfigurationManager(id, configHome);
        this.rec = new Reconfiguration(id);
    }

    private int loadID(String configHome){
        try{
            String path =  "";
            String sep = System.getProperty("file.separator");
            if(configHome == null || configHome.equals("")){
                   path = "config"+sep+"system.config";
            }else{
                  path = configHome+sep+"system.config";
            }
            FileReader fr = new FileReader(path);
            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            while((line = rd.readLine()) != null){
                if(!line.startsWith("#")){
                    StringTokenizer str = new StringTokenizer(line,"=");
                    if(str.countTokens() > 1 && 
                            str.nextToken().trim().equals("system.ttp.id")){
                        fr.close();
                        rd.close();
                        return Integer.parseInt(str.nextToken().trim());
                    }
                }
            }
            fr.close();
            rd.close();
            return -1;
        }catch(Exception e){
            e.printStackTrace(System.out);
            return -1;
        }
    }
     
    public void addServer(int id, String ip, int port){
         rec.addServer(id, ip, port);
         addIds.add(id);
    }
    
    public void removeServer(int id){
        rec.removeServer(id);
    }

    public void setF(int f){
      rec.setF(f);
    }
      
    public void executeUpdates(){
        ReconfigureReply r = rec.execute();
        View v = r.getView();
        System.out.println("New view f: "+v.getF());
        
        TTPMessage msg = new TTPMessage(id,r);
        
        //int[] dest = new int[addIds.size()];
        sendResponse(addIds.toArray(new Integer[1]),msg);    
        
        addIds.clear();
        
   }

     private ServerConnection getConnection(int remoteId) {
        ServerConnection ret = this.connections.get(remoteId);
        if (ret == null) {
            ret = new ServerConnection(manager, null, remoteId, null, null);
            this.connections.put(remoteId, ret);
        }
        return ret;
    }

    
    
     public void sendResponse(Integer[] targets, TTPMessage sm) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        try {
            new ObjectOutputStream(bOut).writeObject(sm);
        } catch (IOException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        }

        byte[] data = bOut.toByteArray();

        for (Integer i : targets) {
            //br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Sending msg to replica "+i);
            try {
                if (i.intValue() != id) {
                    getConnection(i.intValue()).send(data);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    //br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Finished sending messages to replicas");
    }

    
    
    
    
    public void close(){
        rec.close();
    }
    
    
    public static void main(String[] args){
        
        TTP ttp = null;
        
        if(args.length > 0){
            ttp = new TTP(args[0]);
        }else{
            ttp = new TTP("");
        }
        
        ttp.addServer(4, "127.0.0.1", 11040);
        
        
        
        ttp.removeServer(3);
        
        
        ttp.executeUpdates();
        ttp.close();
        System.exit(0);
    }
    
    
    
    
}
