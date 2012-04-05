/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.reconfiguration.util;
import navigators.smart.tom.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;


public class HostsConfig {
    
    private Hashtable servers = new Hashtable();
    
    
    /** Creates a new instance of ServersConfig */
    public HostsConfig(String configHome, String fileName) {
        loadConfig(configHome, fileName);
    }
    
    private void loadConfig(String configHome, String fileName){
        try{
            String path =  "";
            String sep = System.getProperty("file.separator");
            if(configHome.equals("")){
                   if (fileName.equals(""))
                        path = "config"+sep+"hosts.config";
                   else
                        path = "config"+sep+fileName;
            }else{
                   if (fileName.equals(""))
                        path = configHome+sep+"hosts.config";
                   else
                       path = configHome+sep+fileName;
            }
            FileReader fr = new FileReader(path);
            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            while((line = rd.readLine()) != null){
                if(!line.startsWith("#")){
                    StringTokenizer str = new StringTokenizer(line," ");
                    if(str.countTokens() > 2){
                        int id = Integer.valueOf(str.nextToken());
                        String host = str.nextToken();
                        int port = Integer.valueOf(str.nextToken());
                        this.servers.put(id, new Config(id,host,port));
                    }
                }
            }
            fr.close();
            rd.close();
        }catch(Exception e){
            e.printStackTrace(System.out);
        }
    }
    
    public void add(int id, String host, int port){
        if(this.servers.get(id) == null){
            this.servers.put(id, new Config(id,host,port));
        }
    }
    
    public int getNum(){
        return servers.size();
    }
    
    public InetSocketAddress getRemoteAddress(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return new InetSocketAddress(c.host,c.port);
        }
        return null;
    }
    
    
    public InetSocketAddress getServerToServerRemoteAddress(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return new InetSocketAddress(c.host,c.port+1);
        }
        return null;
    }
    
    
    public int getPort(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return c.port;
        }
        return -1;
    }

     public int getServerToServerPort(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return c.port+1;
        }
        return -1;
    }

    
    
    public int[] getHostsIds(){
         Set s = this.servers.keySet();
         int[] ret = new int[s.size()];
         Iterator it = s.iterator();
         int p = 0;
         while(it.hasNext()){
            ret[p] = Integer.parseInt(it.next().toString());
            p++;
         }
         return ret;
    }
    
    
    public void setPort(int id, int port){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            c.port = port;
        }
    }
    
    public String getHost(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return c.host;
        }
        return null;
    }
    
    
    public InetSocketAddress getLocalAddress(int id){
        Config c = (Config) this.servers.get(id);
        if(c != null){
            return new InetSocketAddress(c.port);
        }
        return null;
    }
    
    public class Config{
        public int id;
        public String host;
        public int port;
        
        public Config(int id, String host, int port){
            this.id = id;
            this.host = host;
            this.port = port;
        }
    }
}
