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

package navigators.smart.statemanagment;

/**
 * This classes serves as a log for the state associated with the last checkpoint, and the message
 * batches received since the same checkpoint until the present. The state associated with the last
 * checkpoint together with all the batches of messages received so far, comprises this replica
 * current state
 * 
 * @author Jo�o Sousa
 */
public class StateLog {

    private byte[][] messageBatches; // batches received since the last checkpoint.
    private int lastEid; // Execution ID for the last checkpoint
    private byte[] state; // State associated with the last checkpoint
    private int k; // checkpoint period
    private int position; // next position in the array of batches to be written
    private int execCounter; // number of executions so far

    /**
     * Constructs a State log
     * @param k The chekpoint period
     */
    public StateLog(int k) {

        this.k = k;
        this.messageBatches = new byte[k][];
        this.lastEid = 0;
        this.state = null;
        this.position = 0;
        this.execCounter = 0;
    }
    
    /**
     * Sets the state associated with the last checkpoint, and updates the execution ID associated with it
     * @param state State associated with the last checkpoint
     */
    public void newCheckpoint(byte[] state) {

        for (int i = 0; i < k; i++)
            messageBatches[i] = null;

        position = 0;
        lastEid += k;
        execCounter++;
        this.state = state;
    }

    /**
     * Retrieves the execution ID for the last checkpoint
     * @return Execution ID for the last checkpoint, or -1 if no checkpoint was yet executed
     */
    public int getCurrentCheckpointEid() {
        
        return lastEid - 1;
    }

    /**
     * Retrieves the state associated with the last checkpoint
     * @return State associated with the last checkpoint
     */
    public byte[] getState() {
        return state;
    }

    /**
     * Adds a message batch to the log. This batches should be added to the log
     * in the same order in which they are delivered to the application. Only
     * the 'k' batches received after the last checkpoint are supposed to be kept
     * @param batch The batch of messages to be kept.
     * @return True if the batch was added to the log, false otherwise
     */
    public boolean addMessageBatch(byte[] batch) {

        if (position < k) {

            messageBatches[position] = batch;

            /************************* TESTE *************************
            System.out.println("posicao: " + position);
            System.out.println("execucoes: " + execCounter);
            /************************* TESTE *************************/

            position++;
            execCounter++;

            return true;
        }

        return false;
    }

    /**
     * Returns a batch of messages, given its correspondent execution ID
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    public byte[] getMessageBatch(int eid) {
        if (eid >= lastEid && eid <= execCounter) {
            return messageBatches[eid - lastEid];
        }
        else return null;
    }

    /**
     * Retrieves all the stored batches kept since the last checkpoint
     * @return All the stored batches kept since the last checkpoint
     */
    public byte[][] getMessageBatches() {
        return messageBatches;
    }

    /**
     * Constructs a TransferableState using this log information
     * @param eid Execution ID correspondent to desired state
     * @return TransferableState Object containing this log information
     */
    public TransferableState getTransferableState(int eid) {

        //System.out.println("A devolver o estado!");
        //System.out.println("EID pedido: " + eid);
        //System.out.println("Execucoes feitas ate agora: " + execCounter);
        //System.out.println("Ultimo checkpoint: " + lastEid);
        //System.exit(0);

        if (eid >= lastEid && eid <= execCounter) {

            byte[][] batches = new byte[eid - lastEid + 1][];

            for (int i = 0; i < eid - lastEid; i++)
                batches[i] = messageBatches[i];

            return new TransferableState(batches, lastEid, state);

        }
        else return null;
    }

    /**
     * Updates this log, according to the information contained in the TransferableState object
     * @param transState TransferableState object containing the information which is used to updated this log
     */
    public void update(TransferableState transState) {

        for (int i = 0; i < transState.getMessageBatches().length; i++, position = i) {
            this.messageBatches[i] = transState.getMessageBatches()[i];
        }

        this.lastEid = transState.getCurrentCheckpointEid() + 1;

        this.state = transState.getState();

        this.execCounter = this.lastEid + position;
    }
}
