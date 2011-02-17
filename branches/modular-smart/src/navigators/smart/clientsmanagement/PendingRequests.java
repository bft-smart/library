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

package navigators.smart.clientsmanagement;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

import navigators.smart.tom.core.messages.TOMMessage;



/**
 * Extended LinkedList used to store pending requests issued by a client.
 *
 * @author alysson
 */
public class PendingRequests extends LinkedList<TOMMessage> {

    public PendingRequests() {
    }

    public TOMMessage remove(byte[] serializedMessage) {
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(Arrays.equals(serializedMessage,msg.serializedMessage)) {
                li.remove();
                return msg;
            }
        }
        return null;
    }

    public TOMMessage removeById(int id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId() == id) {
                li.remove();
                return msg;
            }
        }
        return null;
    }

     // I think this method can be removed in future versions of JBP
    public int[] getIds(){
        int ids[] = new int[size()];
        for(int i = 0; i < ids.length; i++){
            ids[i] = get(i).getId();
        }

        return ids;
    }

    public TOMMessage get(byte[] serializedMessage){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(Arrays.equals(serializedMessage,msg.serializedMessage)) {
                return msg;
            }
        }
        return null;
    }


    public TOMMessage getById(int id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId() == id) {
                return msg;
            }
        }
        return null;
    }

    public boolean contains(int id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId() == id) {
                return true;
            }
        }
        return false;
    }
}
