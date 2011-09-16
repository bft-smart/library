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

package navigators.smart.tom.leaderchange;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SignedObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.paxosatwar.executionmanager.RoundValuePair;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 *
 * This class implements a manager of information related to the leader change protocol
 * It also implements some predicates and methods necessary for the protocol.
 * 
 * @author Joao Sousa
 */
public class LCManager {

    //timestamp info
    private int lastts;
    private int nextts;

    //locks
    private ReentrantLock lasttsLock;
    private ReentrantLock nexttsLock;
    private ReentrantLock StopsLock;

    //requests that timed out
    private List<TOMMessage> currentRequestTimedOut = null;

    //data structures for info in stop, sync and catch-up messages
    private HashMap<Integer,HashSet<Integer>> stops;
    private HashMap<Integer,HashSet<LastEidData>> lastEids;
    private HashMap<Integer,HashSet<SignedObject>> collects;

    //stuff from the TOM layer that this object needss
    private ReconfigurationManager reconfManager;
    private MessageDigest md;
    private TOMLayer tomLayer;

    /**
     * Constructor
     *
     * @param reconfManager The reconfiguration manager from TOM layer
     * @param md The message digest engine from TOM layer
     */
    public LCManager(TOMLayer tomLayer,ReconfigurationManager reconfManager, MessageDigest md) {
        this.tomLayer = tomLayer;
        this.lastts = 0;
        this.nextts = 0;

        this.lasttsLock = new ReentrantLock();
        this.nexttsLock = new ReentrantLock();
        this.StopsLock = new ReentrantLock();

        this.stops = new HashMap<Integer,HashSet<Integer>>();
        this.lastEids = new HashMap<Integer, HashSet<LastEidData>>();
        this.collects = new HashMap<Integer, HashSet<SignedObject>>();

        this.reconfManager = reconfManager;
        this.md = md;
    }

    /**
     * Get lock for STOP info
     */
    public void StopsLock() {
        StopsLock.lock();
    }

    /**
     * Release lock for STOP info
     */
    public void StopsUnlock() {
        StopsLock.unlock();
    }

    /**
     * Get lock for lastts
     */
    public void lasttsLock() {
        lasttsLock.lock();
    }

    /**
     * Release lock for lastts
     */
    public void lasttsUnlock() {
        lasttsLock.unlock();
    }

    /**
     * Get lock for nextts
     */
    public void nexttsLock() {
        nexttsLock.lock();
    }

    /**
     * Release lock for nextts
     */
    public void nexttsUnlock() {
        nexttsLock.unlock();
    }

    /**
     * This is meant to keep track of timed out messages in this replica
     *
     * @param currentRequestTimedOut
     */
    public void setCurrentRequestTimedOut(List<TOMMessage> currentRequestTimedOut) {
        this.currentRequestTimedOut = currentRequestTimedOut;
    }

    /**
     * Get the timed out messages in this replica
     * @return timed out messages in this replica
     */
    public List<TOMMessage> getCurrentRequestTimedOut() {
        return currentRequestTimedOut;
    }

    /**
     * Set the previous timestamp
     * @param lastts current timestamp
     */
    public void setLastts(int lastts) {
        this.lastts = lastts;
    }

    /**
     * The current timestamp
     * @return current timestamp
     */
    public int getLastts() {
        return lastts;
    }

    /**
     * Set the next timestamp
     * @param nextts next timestamp
     */
    public void setNextts(int nextts) {
        this.nextts = nextts;
    }

    /**
     * The next timestamp
     * @return next timestamp
     */
    public int getNextts() {
        return nextts;
    }

    /**
     * Keep information about an incoming STOP message
     * @param ts the next timestamp
     * @param pid the process that sent the message
     */
    public void addStop(int ts, int pid) {
        HashSet<Integer> pids = stops.get(ts);
        if (pids == null) pids = new HashSet<Integer>();
        pids.add(pid);
        stops.put(ts, pids);
    }

    /**
     * Discard information about STOP messages up to timestamp ts
     * @param ts timestamp up to which to discard messages
     */
    public void removeStops(int ts) {
        Set<Integer> tss = stops.keySet();
        for (int t : tss) {
            if (t <= ts) stops.remove(t);
        }
    }

    /**
     * Get the quantity of stored STOP information
     * @param ts timestamp to be considered
     * @return quantity of stored STOP information for given timestamp
     */
    public int getStopsSize(int ts) {
        HashSet<Integer> pids = stops.get(ts);
        return pids == null ? 0 : pids.size();
    }

    /**
     * Keep last eid from an incoming SYNC message
     * @param ts the current timestamp
     * @param lastEid the last eid data
     */
    public void addLastEid(int ts, LastEidData lastEid) {

        HashSet<LastEidData> last = lastEids.get(ts);
        if (last == null) last = new HashSet<LastEidData>();
        last.add(lastEid);
        lastEids.put(ts, last);
    }

    /**
     * Discard last eid information up to timestamp ts
     * @param ts timestamp up to which to discard information
     */
    public void removeLastEids(int ts) {
        Set<Integer> tss = lastEids.keySet();
        for (int t : tss) {
            if (t <= ts) lastEids.remove(t);
        }
    }

    /**
     * Get the quantity of stored last eid information
     * @param ts timestamp to be considered
     * @return quantity of stored last eid  information for given timestamp
     */
    public int getLastEidsSize(int ts) {
        HashSet<LastEidData> last = lastEids.get(ts);
        return last == null ? 0 : last.size();
    }

    /**
     * Get the set of last eids related to a timestamp
     * @param ts timestamp for the last eid info
     * @return a set of last eid data
     */
    public HashSet<LastEidData> getLastEids(int ts) {
        return lastEids.get(ts);
    }

    /**
     * Defines the set of last eids related to a timestamp
     * @param ts timestamp for the last eid info
     * @param lasts a set of last eid data
     */
    public void setLastEids(int ts, HashSet<LastEidData> lasts) {

        lastEids.put(ts, lasts);
    }

    /**
     * Keep collect from an incoming SYNC message
     * @param ts the current timestamp
     * @param signedCollect the signed collect data
     */
    public void addCollect(int ts, SignedObject signedCollect) {

        HashSet<SignedObject> c = collects.get(ts);
        if (c == null) c = new HashSet<SignedObject>();
        c.add(signedCollect);
        collects.put(ts, c);
    }
    
    /**
     * Discard collect information up to timestamp ts
     * @param ts timestamp up to which to discard information
     */
    public void removeCollects(int ts) {

        Set<Integer> tss = collects.keySet();
        for (int t : tss) {
            if (t <= ts) collects.remove(t);
        }

    }
    
    /**
     * Get the quantity of stored collect information
     * @param ts timestamp to be considered
     * @return quantity of stored collect information for given timestamp
     */
    public int getCollectsSize(int ts) {

        HashSet<SignedObject> c = collects.get(ts);
        return c == null ? 0 : c.size();
    }

    /**
     * Get the set of collects related to a timestamp
     * @param ts timestamp for collects
     * @return a set of collect data
     */
    public HashSet<SignedObject> getCollects(int ts) {
        return collects.get(ts);
    }

    /**
     * Defines the set of collects related to a timestamp
     * @param ts timestamp for the last eid info
     * @param colls a set of collect data
     */
    public void setCollects(int ts, HashSet<SignedObject> colls) {

        collects.put(ts, colls);
    }
    /**
     * The all-important predicate "sound". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param collects the collect data to which to apply the predicate.
     * @return see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean sound(HashSet<CollectData> collects) {

        if (collects == null) return false;
        
        HashSet<Integer> rounds = new HashSet<Integer>();
        HashSet<byte[]> values = new HashSet<byte[]>();

        for (CollectData c : collects) { // apurar todos os rounds e valores que existem separadamente

            rounds.add(c.getQuorumWeaks().getRound()); //guardar round recebido de um quorum bizantino de weaks

            // guardar valor recebido de um quorum bizantino de weaks, caso nao seja um valor vazio
            if (!Arrays.equals(c.getQuorumWeaks().getValue(), new byte[0])) {
                boolean insert = true; // este ciclo serve para evitar meter valores repetidos no conjunto
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWeaks().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWeaks().getValue());
            }
            for (RoundValuePair rv : c.getWriteSet()) { // guardar todos os rounds e valores escritos
                rounds.add(rv.getRound());

                boolean insert = true; // este ciclo serve para evitar meter valores repetidos no conjunto
                for (byte[] b : values) {

                    if (Arrays.equals(b, rv.getHashedValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(rv.getHashedValue());
            }

        }

        // depois de ter todos os rounds e valores organizados, aplicar o predicado propriamente dito
        for (int r : rounds) {
            for (byte[] v : values) {

                if (binds(r, v, collects)) {

                    return true;
                }
            }
        }

        return unbound(collects);
    }

    /**
     * The predicate "binds". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param round the round to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean binds(int round, byte[] value, HashSet<CollectData> collects) {

        return (value != null && collects != null && collects.size() > (reconfManager.getCurrentViewN() - reconfManager.getCurrentViewF()))
                && quorumHighest(round, value, collects) && certifiedValue(round, value, collects);
    }

    /**
     * Devolve um valor que seja bind, que seja diferente de nulo e com round maior ou igual a zero
     * @param collects Conjunto de collects de onde determinar o valor
     * @return O valor bind
     */
    public byte[] getBindValue(HashSet<CollectData> collects) {

        if (collects == null) return null;

        HashSet<Integer> rounds = new HashSet<Integer>();
        HashSet<byte[]> values = new HashSet<byte[]>();

        for (CollectData c : collects) { // apurar todos os rounds e valores que existem separadamente

            rounds.add(c.getQuorumWeaks().getRound()); //guardar round recebido de um quorum bizantino de weaks

            // guardar valor recebido de um quorum bizantino de weaks, caso nao seja um valor vazio
            if (!Arrays.equals(c.getQuorumWeaks().getValue(), new byte[0])) {
                boolean insert = true; // este ciclo serve para evitar meter valores repetidos no conjunto
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWeaks().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWeaks().getValue());
            }
            for (RoundValuePair rv : c.getWriteSet()) { // guardar todos os rounds e valores escritos
                rounds.add(rv.getRound());

                boolean insert = true; // este ciclo serve para evitar meter valores repetidos no conjunto
                for (byte[] b : values) {

                    if (Arrays.equals(b, rv.getHashedValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(rv.getHashedValue());
            }

        }

        // depois de ter todos os rounds e valores organizados, aplicar o predicado propriamente dito
        for (int r : rounds) {
            for (byte[] v : values) {

                if (r >= 0 && binds(r, v, collects)) { // temos um valor que satisfaz o predicado?

                    // como estamos a lidar com hashes, temos que encontrar o valor original...
                    for (CollectData c : collects) {
                        for (RoundValuePair rv : c.getWriteSet()) {

                            for (byte[] b : values) {

                                if (rv.getValue() != null && Arrays.equals(v, rv.getHashedValue())) {
                                    return rv.getValue();
                                }
                            }

                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * The predicate "unbound". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param collects the collect data to which to apply the predicate.
     * @return see page 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean unbound(HashSet<CollectData> collects) {

        if (collects == null) return false;

        boolean unbound = false;
        int count = 0;

        if (collects.size() >= (reconfManager.getCurrentViewN() - reconfManager.getCurrentViewF())) {


            for (CollectData c : collects) {

                if (c.getQuorumWeaks().getRound() == 0) count++;
            }
        }
        else return false;

        unbound = count > ((reconfManager.getCurrentViewN() + reconfManager.getCurrentViewF()) / 2);
        return unbound;
        
    }

    /**
     * The predicate "quorumHighest". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param round the round to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean quorumHighest(int round, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean appears = false;
        boolean quorum = false;

        for (CollectData c : collects) {

            if (c.getQuorumWeaks().getRound() == round && Arrays.equals(value, c.getQuorumWeaks().getValue())) {

                appears = true;
                break;
            }
        }

        int count = 0;
        for (CollectData c : collects) {

            if ((c.getQuorumWeaks().getRound() < round)
                    || (c.getQuorumWeaks().getRound() == round && Arrays.equals(value, c.getQuorumWeaks().getValue())))
                        count++;

        }

        quorum = count > ((reconfManager.getCurrentViewN() + reconfManager.getCurrentViewF()) / 2);
        
        return appears && quorum;
    }

    /**
     * The predicate "certifiedValue". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param round the round to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see page 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean certifiedValue(int round, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean certified = false;

        int count = 0;
        for (CollectData c : collects) {

            for (RoundValuePair pv : c.getWriteSet()) {

                if (pv.getRound() >= round && Arrays.equals(value, pv.getHashedValue()))
                    count++;
            }

        }

        certified = count > reconfManager.getCurrentViewF();

        return certified;
    }

    /**
     * Fetchs a set of correctly signed and normalized collect data structures
     * @param ts the timestamp from which the collects were stored
     * @param eid the eid to which to normalize the collects
     * @return a set of correctly signed and normalized collect data structures
     */
    public HashSet<CollectData> selectCollects(int ts, int eid) {

        HashSet<SignedObject> c = collects.get(ts);

        if (c == null) return null;

        return normalizeCollects(getSignedCollects(c), eid);
        
    }

    /**
     * Fetchs a set of correctly signed and normalized collect data structures from the
     * specified original set of collects
     * @param signedObjects original set of signed collects
     * @param eid the eid to which to normalize the collects
     * @return a set of correctly signed and normalized collect data structures
     */
    public HashSet<CollectData> selectCollects(HashSet<SignedObject> signedObjects, int eid) {

        if (signedObjects == null) return null;

        return normalizeCollects(getSignedCollects(signedObjects), eid);

    }

    // Filters the correctly signed collects
    private HashSet<CollectData> getSignedCollects(HashSet<SignedObject> signedCollects) {

        HashSet<CollectData> colls = new HashSet<CollectData>();

        for (SignedObject so : signedCollects) {

            CollectData c;
            try {
                c = (CollectData) so.getObject();
                int sender = c.getPid();
                if (tomLayer.verifySignature(so, sender)) {
                    colls.add(c);
                }
            } catch (IOException ex) {
                Logger.getLogger(LCManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(LCManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return colls;

    }

    // Normalizes the set of collects. A set of collects is considered normalized if or when
    // all collects are related to the same eid. This is important because not all replicas
    // may be executing the same eid when tere is a leader change
    private HashSet<CollectData> normalizeCollects(HashSet<CollectData> collects, int eid) {

        HashSet<CollectData> result = new HashSet<CollectData>();

        // se houver collects referentes a outros consensos, assumir que ainda estao no round zero do consenso que queremos
        for (CollectData c : collects) {

            if (c.getEid() == eid) {
                result.add(c);
            }
            else {
                result.add(new CollectData(c.getPid(), eid, new RoundValuePair(0, new byte[0]), new HashSet<RoundValuePair>()));
            }

        }

        // fazer o hash dos valores em write set
        for (CollectData c : result) {

            for (RoundValuePair rv : c.getWriteSet()) {

                if  (rv.getValue() != null && rv.getValue().length > 0)
                    rv.setHashedValue(md.digest(rv.getValue()));
                else rv.setHashedValue(new byte[0]);
            }
        }

        return result;

    }

    /**
     * Gets the highest valid last eid related to the given timestamp
     * @param ts the timestamp
     * @return -1 if there is no such eid, otherwise returns the highest valid last eid
     */
    public LastEidData getHighestLastEid(int ts) {

        LastEidData highest = new LastEidData(-2, -2, null, null);

        HashSet<LastEidData> lasts = lastEids.get(ts);

        if (lasts == null) return null;
       
        for (LastEidData l : lasts) {

            //TODO: FALTA VERIFICAR A PROVA!!!!
            if (l.getEid() > highest.getEid()) highest = l;
        }

        return highest;
    }

    /**
     * Returns the value of the specified last eid for a given timestamp
     * @param ts the related timestamp
     * @param eid the last eid
     * @return null if there is no such eid or is invalid, otherwise returns the value
     */
    public byte[] getLastEidValue(int ts, int eid) {

        HashSet<LastEidData> lasts = lastEids.get(ts);

        if (lasts == null) return null;

        byte[] result = null;

        for (LastEidData l : lasts) {

            if (l.getEid() == eid) {

                //TODO: FALTA VERIFICAR A PROVA!!!!
                result = l.getEidDecision();
                break;
            }
        }

        return result;
    }
}
