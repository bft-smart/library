/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.core.messages;

/**
 * Possible types of TOMMessage
 * 
 * @author alysson
 */
public enum TOMMessageType {
    REQUEST, //0
    READONLY_REQUEST, //1
    REPLY, //2
    RECONFIG; //3
    
    public int toInt() {
        switch(this) {
            case REQUEST: return 0;
            case READONLY_REQUEST: return 1;
            case REPLY: return 2;
            default: return 3;
        }        
    }
    
    public static TOMMessageType fromInt(int i) {
        switch(i) {
            case 0: return REQUEST;
            case 1: return READONLY_REQUEST;
            case 2: return REPLY;
            default: return RECONFIG;
        }            
    }
}
