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
package navigators.smart.tom;

//import br.ufsc.das.communication.SimpleCommunicationSystem;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.reconfiguration.ReconfigureReply;
import navigators.smart.reconfiguration.View;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMUtil;
/**
 * This class implements a TOMReplyReceiver, and a proxy to be used on the client side of the application.
 * It sends a request to the replicas, receives the reply, and delivers it to the
 * application
 *
 */
public class ServiceProxy extends TOMSender {

    //******* EDUARDO BEGIN **************//
    // private int n; // Number of total replicas in the system
    // private int f; // Number of maximum faulty replicas assumed to occur

    // Semaphores used to render this object thread-safe
    // TODO: o mutex nao poderia ser antes um reentrantlock, em vez de um semaforo?
    //Eduardo: Acho que pode até ser um reentrantlock, mas apenas pode ser liberado (no caso do semaforo é a mesma coisa)
    //após a operação completar (não pode ser liberado após apenas o envio pq reqId será perdido)
    private Semaphore mutex = new Semaphore(1);
    private Semaphore mutexToSend = new Semaphore(1);
    //******* EDUARDO END **************//

    private Semaphore sm = new Semaphore(0);
    private int reqId = -1; // request id
    private TOMMessage replies[] = null; // Replies from replicas are stored here
    private TOMMessage response = null; // Pointer to the reply that is actually delivered to the application

    /**
     * Constructor
     *
     * @param id Process id for this client
     */
    public ServiceProxy(int processId) {
        init(processId);
        replies = new TOMMessage[getViewManager().getCurrentViewN()];
    }

    /**
     * Constructor
     *
     * @param id Process id for this client
     * @param configHome Configuration directory for JBP
     * TODO: E mesmo a directoria do JBP, ou de alguma biblioteca de terceiros?
     */
    public ServiceProxy(int processId, String configHome) {
        init(processId, configHome);
        replies = new TOMMessage[getViewManager().getCurrentViewN()];
    }


    //******* EDUARDO BEGIN **************//
    /**
     * This method sends a request to the replicas, and returns the related reply. This method is
     * thread-safe.
     *
     * @param request Request to be sent
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request) {
        return invoke(request, ReconfigurationManager.TOM_NORMAL_REQUEST, false);
    }

    /*public byte[] invoke(byte[] request) {
    return invoke(request,false);
    }*/
    public byte[] invoke(byte[] request, boolean readOnly) {
        return invoke(request, ReconfigurationManager.TOM_NORMAL_REQUEST, readOnly);
    }
    //******* EDUARDO END **************//


    /**
     * This method sends a request to the replicas, and returns the related reply. This method is
     * thread-safe.
     *
     * @param request Request to be sent
     * @param readOnly it is a read only request (will not be ordered)
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request, int reqType, boolean readOnly) {

        // Ahead lies a critical section.
        // This ensures the thread-safety by means of a semaphore
        try {
            this.mutexToSend.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Discard previous replies
        Arrays.fill(replies, null);
        response = null;
        // Send the request to the replicas, and get its ID
        doTOMulticast(request, reqType, readOnly);
        reqId = getLastSequenceNumber();

        // Critical section ends here. The semaphore can be released
        //NAO PODE LIBERAR O SEMAFORO AQUI
        //this.mutex.release();

        // This instruction blocks the thread, until a response is obtained.
        // The thread will be unblocked when the method replyReceived is invoked
        // by the client side communication system
        try {
            this.sm.acquire();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        //******* EDUARDO BEGIN **************//

        byte[] ret = null;
        if (reqType == ReconfigurationManager.TOM_NORMAL_REQUEST) {
            //Reply to a normal request!
            if (response.getViewID() == getViewManager().getCurrentViewId()) {
                ret = response.getContent(); // return the response
                mutexToSend.release();
                return ret;
            } else { // if(response.getViewID() > getViewManager().getCurrentViewId()){
                //updated view received
                reconfigureTo((View) TOMUtil.getObject(response.getContent()));
                mutexToSend.release();
                return invoke(request, reqType, readOnly);
            }
        } else {
            //Reply to a reconfigure request!
            System.out.println("Recebeu reply para uma reconfigure request!");
            //É "impossivel" ser menor!
            if (response.getViewID() > getViewManager().getCurrentViewId()) {
                Object r = TOMUtil.getObject(response.getContent());
                if(r instanceof View){ //Não executou esta requisição pq a visao estava desatualizada
                    reconfigureTo((View) r);
                    mutexToSend.release();
                    return invoke(request, reqType, readOnly);
                }else{ //Executou a requisição de reconfiguração
                    reconfigureTo(((ReconfigureReply)r).getView());
                    ret = response.getContent(); // return the response
                    mutexToSend.release();
                    return ret;
                }
            }else{
                //Caso a reconfiguração nao foi executada porque algum parametro
                // da requisição estava incorreto: o processo queria fazer algo que nao é permitido
                ret = response.getContent(); // return the response
                mutexToSend.release();
                return ret;
            }
        }
        //******* EDUARDO END **************//
    }

    //******* EDUARDO BEGIN **************//
    private void reconfigureTo(View v){
        System.out.println("Recebeu uma visão mais atual!");
        getViewManager().reconfigureTo(v);
        replies = new TOMMessage[getViewManager().getCurrentViewN()];
        getCommunicationSystem().updateConnections();
    }
    //******* EDUARDO END **************//
    /**
     * This is the method invoked by the client side comunication system.
     *
     * @param reply The reply delivered by the client side comunication system
     */
    public void replyReceived(TOMMessage reply) {


        // Ahead lies a critical section.
        // This ensures the thread-safety by means of a semaphore
        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        int sender = reply.getSender();

         //******* EDUARDO BEGIN **************//
        int pos = getViewManager().getCurrentViewPos(sender);
         //******* EDUARDO END **************//

        if (pos < 0) { //ignore messages that don't come from replicas
            return;
        }


        //System.out.println("Recebeu reply de "+sender+" pos: "+pos+" reqId:"+reply.getSequence());

        if (reply.getSequence() == reqId) { // Is this a reply for the last request sent?
            //System.out.println("Vai contar reply de "+sender+" pos: "+pos+" reqId:"+reqId);
            replies[pos] = reply;
            // Compare the reply just received, to the others
            int sameContent = 1;
            for (int i = 0; i < replies.length; i++) {
                if (i != pos && replies[i] != null && Arrays.equals(replies[i].getContent(), reply.getContent())) {
                    sameContent++;
                     //******* EDUARDO BEGIN **************//
                    if (sameContent >= getViewManager().getCurrentViewF() + 1) {
                     //******* EDUARDO END **************//
                        break;
                    }
                }/*else{
                    if(i != pos && replies[i] != null){
                    System.out.println("Resposta diferente......... "+sender);
                    }
                }*/
            }

            //System.out.println("contador "+sameContent);

             //******* EDUARDO BEGIN **************//
            // Is there already more than f equal replies?
            if (sameContent >= getViewManager().getCurrentViewF() + 1) {
                response = reply;
                reqId = -1;
                //this.mutexToSend.release();
                this.sm.release(); // unblocks the thread that is executing the "invoke" method,
            // so it can deliver the reply to the application
            }
            //******* EDUARDO END **************//
        }
        // Critical section ends here. The semaphore can be released
        this.mutex.release();
    }
}

