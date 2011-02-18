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

/**
 * This interface is used by components that want to be informed upon starting
 * and final descision of client requests.
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface ClientRequestListener {

    /**
     * This method is invoked upon a new message beeing received from a client
     * that shall be ordered/decided by the consensus
     */
    public void requestReceived(Object msg);

    /**
     * This method is invoked when the consensus successfully decided upon the
     * message.
     */
    public void requestOrdered(Object msg);

}
