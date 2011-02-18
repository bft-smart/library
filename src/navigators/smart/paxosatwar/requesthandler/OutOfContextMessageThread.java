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

package navigators.smart.paxosatwar.requesthandler;

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;

/**
 * This thread manages out of context messages
 */
public class OutOfContextMessageThread extends Thread {
	
	private static final Logger log = Logger.getLogger(OutOfContextMessageThread.class.getCanonicalName());

    private RequestHandler requesthandler; // requesthandler
    private final ExecutionManager execMng; // execmng

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    private ReentrantLock outOfContextLock = new ReentrantLock();

    public void outOfContextLock() {
        outOfContextLock.lock();
        //Logger.println("(OutOfContextMessageThread.OutOfContextLock) Out of context lock obtained");
    }

    public void outOfContextUnlock() {
        outOfContextLock.unlock();
        //Logger.println("(OutOfContextMessageThread.OutOfContextUnlock) Out of context lock released");
    }

    /******************************************************************/

    /**
     * Creates a new instance of OutOfContextMessageThread
     * @param execmng The Executionmanager
     */
    public OutOfContextMessageThread(ExecutionManager execmng) {
        super("Out of Context Thread");
        this.execMng = execmng;
        this.requesthandler = execmng.getRequestHandler();
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

                    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
                    if (!outOfContextLock.isLocked())
                    /******************************************************************/
                        requesthandler.waitForPaxosToFinish();
                }

                /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
                outOfContextLock();
                /******************************************************************/

                long nextExecution = requesthandler.getLastExec() + 1;
                if (execMng.thereArePendentMessages(nextExecution)) {
                    if(log.isLoggable(Level.FINER))
                        log.finer("Starting processing out of context messages for consensus " + nextExecution);
                    execution = execMng.getExecution(nextExecution);
                    if(log.isLoggable(Level.FINER))
                        log.finer("Finished processing out fo context messages for consensus " + nextExecution);
                }

                /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
                outOfContextUnlock();
                /******************************************************************/
                
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(OutOfContextMessageThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
