/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.core.messages;

/**
 * Possible types of TOMMessage
 * 
 * @author alysson
 */
public enum TOMMessageType {
    ORDERED_REQUEST, //0
    UNORDERED_REQUEST, //1
    REPLY, //2
    RECONFIG, //3
    ASK_STATUS, // 4
    STATUS_REPLY; // 5
    
    public int toInt() {
        switch(this) {
            case ORDERED_REQUEST: return 0;
            case UNORDERED_REQUEST: return 1;
            case REPLY: return 2;
            case RECONFIG: return 3;
            case ASK_STATUS: return 4;
            case STATUS_REPLY: return 5;
            default: return -1;
        }
    }
    
    public static TOMMessageType fromInt(int i) {
        switch(i) {
            case 0: return ORDERED_REQUEST;
            case 1: return UNORDERED_REQUEST;
            case 2: return REPLY;
            case 3: return RECONFIG;
            case 4: return ASK_STATUS;
            case 5: return STATUS_REPLY;
            default: return RECONFIG;
        }            
    }
}
