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

package navigators.smart.communication.client;

import navigators.smart.communication.client.netty.NettyClientServerCommunicationSystemServerSide;
import navigators.smart.tom.util.TOMConfiguration;

/**
 *
 * @author Paulo
 */
public class CommunicationSystemServerSideFactory {

    public static CommunicationSystemServerSide getCommunicationSystemServerSide(TOMConfiguration conf){
         if (conf.clientServerCommSystem() == 1){
            return new NettyClientServerCommunicationSystemServerSide(conf);
         }
        
         System.out.println("Error: no client-server communication system is defined");
         return null;
    }

}
