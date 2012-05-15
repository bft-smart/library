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
    RECONFIG; //3
    
    public int toInt() {
        switch(this) {
            case ORDERED_REQUEST: return 0;
            case UNORDERED_REQUEST: return 1;
            case REPLY: return 2;
            default: return 3;
        }
    }
    
    public static TOMMessageType fromInt(int i) {
        switch(i) {
            case 0: return ORDERED_REQUEST;
            case 1: return UNORDERED_REQUEST;
            case 2: return REPLY;
            default: return RECONFIG;
        }            
    }
}
