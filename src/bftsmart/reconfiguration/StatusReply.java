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
