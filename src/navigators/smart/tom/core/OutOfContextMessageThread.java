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

package navigators.smart.tom.core;

import java.util.logging.Level;

import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.tom.util.Logger;


/**
 * This thread manages out of context messages
 */
public class OutOfContextMessageThread extends Thread {

    private TOMLayer tomLayer; // TOM layer

    /**
     * Creates a new instance of OutOfContextMessageThread
     * @param tomLayer the TOM layer
     */
    public OutOfContextMessageThread(TOMLayer tomLayer) {
        super("Out of Context Thread");

        this.tomLayer = tomLayer;
    }

    /**
     * This is the code for the thread. What it does, is wait for the PaW algorithm to finished. When it is finished,
     * it forces the execution manager to process out of context message, by invoking the 'getExecution' method.
     * It also
     */
    @Override
    public void run() {
        Execution execution = null;

        while(true) {
            try {
                if (execution == null || !execution.isDecided()) {
                    tomLayer.waitForPaxosToFinish();
                }

                int nextExecution = tomLayer.getLastExec() + 1;
                if (tomLayer.execManager.thereArePendentMessages(nextExecution)) {
                    System.out.println("[OutOfContextMessageThread.run]");
                    System.out.println("Vou processar mensagens q estavam fora do contexto para o EID " + nextExecution);
                    System.out.println("[/OutOfContextMessageThread.run]");
                    Logger.println("(OutOfContextMessageThread.run) starting processing out of context messages for consensus " + nextExecution);
                    execution = tomLayer.execManager.getExecution(nextExecution);
                    Logger.println("(OutOfContextMessageThread.run) finished processing out fo context messages for consensus " + nextExecution);
                }

                Thread.sleep(5);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(OutOfContextMessageThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
