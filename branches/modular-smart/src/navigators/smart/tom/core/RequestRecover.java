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

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import navigators.smart.tom.core.messages.RequestRecoveryMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * TODO: Isto vai desaparecer?
 * 
 */
public class RequestRecover {

    private Semaphore wait = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);
    private int[] group;
    private TOMMessage msg;
    private TOMLayer tomLayer;
    private byte[] hash;
    private TOMConfiguration conf;

    /** Creates a new instance of RequestRecover */
    public RequestRecover(TOMLayer tomLayer, TOMConfiguration conf) {
        this.tomLayer = tomLayer;
        this.conf = conf;
        group = new int[conf.getN() - 1];

        int c = 0;
        for (int i = 0; i < conf.getN(); i++) {
            if (i != conf.getProcessId()) {
                group[c++] = i;
            }
        }
    }

    public void recover(byte[] hash) {
        this.msg = null;
        this.hash = hash;
        this.tomLayer.getCommunication().send(group,
                new RequestRecoveryMessage(hash, conf.getProcessId()));
    }

    public void receive(TOMMessage msg, byte[] msgHash) {
        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.hash != null) {
            if (Arrays.equals(msgHash, hash)) {
                this.msg = msg;
                this.hash = null;
                //br.ufsc.das.util.Logger.println("(RequestRecover) Mensagem recebida coincide com a que estavamos ah espera");
                this.wait.release();
            } else {
                //br.ufsc.das.util.Logger.println("(RequestRecover) Mensagem recebida NAO coincide com a que estavamos ah espera");
            }
        }

        mutex.release();
    }

//    public TOMMessage checkWithoutWait(List<RequestRecoveryMessage> l, byte[] h) {
        /*
        TOMMessage result = null;

        try{
        this.mutex.acquire();
        }catch(Exception e){
        e.printStackTrace();
        }

        if(l != null && l.size() > conf.getF()){        
        List<TOMMessage> candidates = new LinkedList<TOMMessage>();

        for(int i = 0; i < l.size(); i++){
        if(se != null){

        for(UnorderedEntry e: se){

        if(equalsHash(e.hash,h)){

        candidates.add(e.msg);

        break;

        }

        }

        }

        }



        result = getFromCandidates(candidates);

        }



        this.mutex.release();



        return result;
         */
//        return null;
//    }

//    public void check(List<RequestRecoveryMessage> l) {
        /*
        try{

        this.mutex.acquire();

        }catch(Exception e){

        e.printStackTrace();

        }



        if(this.hash != null && l != null && l.size() > conf.getF()){

        List<TOMMessage> candidates = new LinkedList<TOMMessage>();



        for(int i = 0; i < l.size(); i++){

        UnorderedEntry[] se = l.get(i).getMsgsDelivered();



        if(se != null){

        for(UnorderedEntry e:se){

        if(equalsHash(e.hash,this.hash)){

        candidates.add(e.msg);

        break;

        }

        }

        }

        }



        TOMMessage result = getFromCandidates(candidates);

        if(result != null){

        this.msg = result;

        this.hash = null;

        this.wait.release();

        }

        }



        this.mutex.release();
         */
//    }

//    private TOMMessage getFromCandidates(List<TOMMessage> candidates) {
//        TOMMessage comp = null;
//
//        for (int i = 0; i < candidates.size(); i++) {
//            comp = candidates.get(i);
//            int c = 1;
//            for (int j = i + 1; j < candidates.size(); j++) {
//                if (comp.equals(candidates.get(j))) {
//                    c++;
//                }
//            }
//            if (c > conf.getF()) {
//                return comp;
//            }
//        }
//
//        return null;
//    }

    /*
    public void receive(TOMMessage msg) {
        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.hash != null) {

            byte[] recHash = tomLayer.computeHash(msg);
            if (Arrays.equals(recHash, hash)) {
                //message recovered...
                this.msg = msg;
                this.hash = null;
                this.wait.release();
            }
        } else {
            Logger.println("(RequestRecover) hash do RR_REPLY não é igual ao do RR_REQUEST");
        }

        mutex.release();
    }
    */
    public TOMMessage getMsg() {
        try {
            this.wait.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return this.msg;
    }
}
