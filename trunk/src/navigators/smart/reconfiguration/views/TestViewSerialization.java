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

package navigators.smart.reconfiguration.views;


import java.net.InetSocketAddress;




public class TestViewSerialization {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        int[] ids = {1,2,3,4};
        InetSocketAddress[] in = new InetSocketAddress[4];
        in[0] = new InetSocketAddress("127.0.0.1",1234);
        in[1] = new InetSocketAddress("127.0.0.1",1234);
        in[2] = new InetSocketAddress("127.0.0.1",1234);
        in[3] = new InetSocketAddress("127.0.0.1",1234);
        View v = new View(10, ids,1,in);
        ViewStorage st = new DefaultViewStorage();
        st.storeView(v);
        
        View r = st.readView();
        System.out.println(r);
    }

}
