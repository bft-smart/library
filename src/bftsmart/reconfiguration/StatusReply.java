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

public enum StatusReply {
    OFFLINE, //0
    READY, // 1
    RECONFIG, //2
    UPDATING_STATE, //3
    LEADER_CHANGE; //4
    
    public String toString() {
        switch(this) {
            case OFFLINE: return "OFFLINE";
            case READY: return "READY";
            case RECONFIG: return "RECONFIG";
            case UPDATING_STATE: return "UPDATING_STATE";
            case LEADER_CHANGE: return "LEADER_CHANGE";
            default: return "";
        }
    }
    
    public static StatusReply fromString(String i) {
    	if("OFFLINE".equals(i))
            return OFFLINE;
    	else if("READY".equals(i))
            return READY;
    	else if("RECONFIG".equals(i))
            return RECONFIG;
    	else if("UPDATING_STATE".equals(i))
            return UPDATING_STATE;
    	else if("LEADER_CHANGE".equals(i))
            return LEADER_CHANGE;
    	else if("OFFLINE".equals(i))
            return OFFLINE;
    	else
    		return OFFLINE;
    }

}
