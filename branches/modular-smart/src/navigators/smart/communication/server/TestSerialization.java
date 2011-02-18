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

package navigators.smart.communication.server;

import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.TOMMessage;


public class TestSerialization {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        // TODO code application logic here
        TOMMessage tm = new TOMMessage(0,0,new String("abc").getBytes());

        


        byte[] message = tm.getBytes();
        System.out.println(message.length);
        System.out.println(message);

        //TOMMessage tm2 = (TOMMessage) ois.readObject();
        ByteBuffer buf = ByteBuffer.wrap(message);
        
        TOMMessage tm2 = new TOMMessage(buf);
        
        assert(tm.equals(tm2));
//        tm2.readExternal(ois);
        
//        System.out.println(new String(tm2.getContent()));
    }

}
