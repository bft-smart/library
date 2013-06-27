/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 *
 * This file is part of BFT-SMaRt.
 *
 * BFT-SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BFT-SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package bftsmart.statemanagement;

import bftsmart.tom.core.DeliveryThread;
import bftsmart.tom.core.TOMLayer;

/**
 * TODO: Don't know if this class will be used. For now, leave it here
 *
 *  Check if the changes for supporting dynamicity are correct
 *  
 * @author Joao Sousa
 */
public interface StateManager {

    public void requestAppState(int eid);
    
    public void analyzeState(int eid);

    public void stateTimeout();
    
    public void init(TOMLayer tomLayer, DeliveryThread dt);
    
    public void SMRequestDeliver(SMMessage msg);
    
    public void SMReplyDeliver(SMMessage msg);
    
    public boolean isRetrievingState();
}
