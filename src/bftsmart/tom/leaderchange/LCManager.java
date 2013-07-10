/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.tom.leaderchange;

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

import bftsmart.paxosatwar.executionmanager.TimestampValuePair;
import bftsmart.paxosatwar.messages.MessageFactory;
import bftsmart.paxosatwar.messages.PaxosMessage;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * This class implements a manager of information related to the leader change protocol
 * It also implements some predicates and methods necessary for the protocol.
 * 
 * @author Joao Sousa
 */
public class LCManager {

    //timestamp info
    private int lastreg;
    private int nextreg;

    //requests that timed out
    private List<TOMMessage> currentRequestTimedOut = null;

    //data structures for info in stop, sync and catch-up messages
    private HashMap<Integer,HashSet<Integer>> stops;
    private HashMap<Integer,HashSet<LastEidData>> lastEids;
    private HashMap<Integer,HashSet<SignedObject>> collects;

    //stuff from the TOM layer that this object needss
    private ServerViewManager SVManager;
    private MessageDigest md;
    private TOMLayer tomLayer;
    
    private int currentLeader;
    private Cipher cipher;
    
    /**
     * Constructor
     *
     * @param reconfManager The reconfiguration manager from TOM layer
     * @param md The message digest engine from TOM layer
     */
    public LCManager(TOMLayer tomLayer,ServerViewManager reconfManager, MessageDigest md) {
        this.tomLayer = tomLayer;
        this.lastreg = 0;
        this.nextreg = 0;
        this.currentLeader = 0;

        this.stops = new HashMap<Integer,HashSet<Integer>>();
        this.lastEids = new HashMap<Integer, HashSet<LastEidData>>();
        this.collects = new HashMap<Integer, HashSet<SignedObject>>();

        this.SVManager = reconfManager;
        this.md = md;

        try {
            this.cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
            ex.printStackTrace();
        }

    }
    public int getNewLeader() {

        int[] proc = SVManager.getCurrentViewProcesses();
        int minProc = proc[0];
        int maxProc = proc[0];
                    
        for (int p : proc) {
            if (p < minProc) minProc = p;
            if (p > maxProc) maxProc = p;
        }
 
        
        do {
            currentLeader++;
            if (currentLeader > maxProc) {

                currentLeader = minProc;    
            }
        } while(!SVManager.isCurrentViewMember(currentLeader));
        
        return currentLeader;
    }
    
    public void setNewLeader(int leader) {
        currentLeader = leader;
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
     * Set the previous regency
     * @param lastreg current regency
     */
    public void setLastReg(int lastreg) {
        this.lastreg = lastreg;
    }

    /**
     * The current regency
     * @return current regency
     */
    public int getLastReg() {
        return lastreg;
    }

    /**
     * Set the next regency
     * @param nextts next regency
     */
    public void setNextReg(int nextreg) {
        this.nextreg = nextreg;
    }

    /**
     * The next regency
     * @return next regency
     */
    public int getNextReg() {
        return nextreg;
    }

    /**
     * Keep information about an incoming STOP message
     * @param regency the next regency
     * @param pid the process that sent the message
     */
    public void addStop(int regency, int pid) {
        HashSet<Integer> pids = stops.get(regency);
        if (pids == null) pids = new HashSet<Integer>();
        pids.add(pid);
        stops.put(regency, pids);
    }

    /**
     * Discard information about STOP messages up to specified regency
     * @param ts timestamp up to which to discard messages
     */
    public void removeStops(int regency) {
        Integer[] keys = new Integer[stops.keySet().size()];
        stops.keySet().toArray(keys);

        for (int i = 0 ; i < keys.length; i++) {
            if (keys[i] <= regency) stops.remove(keys[i]);
        }
    }

    /**
     * Get the quantity of stored STOP information
     * @param regency Regency to be considered
     * @return quantity of stored STOP information for given timestamp
     */
    public int getStopsSize(int regency) {
        HashSet<Integer> pids = stops.get(regency);
        return pids == null ? 0 : pids.size();
    }

    /**
     * Keep last eid from an incoming SYNC message
     * @param regency the current regency
     * @param lastEid the last eid data
     */
    public void addLastEid(int regency, LastEidData lastEid) {

        HashSet<LastEidData> last = lastEids.get(regency);
        if (last == null) last = new HashSet<LastEidData>();
        last.add(lastEid);
        lastEids.put(regency, last);
    }

    /**
     * Discard last eid information up to the specified regency
     * @param regency Regency up to which to discard information
     */
    public void removeLastEids(int regency) {
        Integer[] keys = new Integer[lastEids.keySet().size()];
        lastEids.keySet().toArray(keys);

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] <= regency) lastEids.remove(keys[i]);
        }
    }

    /**
     * Get the quantity of stored last eid information
     * @param regency regency to be considered
     * @return quantity of stored last eid  information for given regency
     */
    public int getLastEidsSize(int regency) {
        HashSet<LastEidData> last = lastEids.get(regency);
        return last == null ? 0 : last.size();
    }

    /**
     * Get the set of last eids related to a regency
     * @param regency Regency for the last eid info
     * @return a set of last eid data
     */
    public HashSet<LastEidData> getLastEids(int regency) {
        return lastEids.get(regency);
    }

    /**
     * Defines the set of last eids related to a regency
     * @param regency Regency for the last eid info
     * @param lasts a set of last eid data
     */
    public void setLastEids(int regency, HashSet<LastEidData> lasts) {

        lastEids.put(regency, lasts);
    }

    /**
     * Keep collect from an incoming SYNC message
     * @param ts the current regency
     * @param signedCollect the signed collect data
     */
    public void addCollect(int regency, SignedObject signedCollect) {

        HashSet<SignedObject> c = collects.get(regency);
        if (c == null) c = new HashSet<SignedObject>();
        c.add(signedCollect);
        collects.put(regency, c);
    }
    
    /**
     * Discard collect information up to the given regency
     * @param regency Regency up to which to discard information
     */
    public void removeCollects(int regency) {

        Integer[] keys = new Integer[collects.keySet().size()];
        collects.keySet().toArray(keys);

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] <= regency) collects.remove(keys[i]);
        }
    }
    
    /**
     * Get the quantity of stored collect information
     * @param regency Regency to be considered
     * @return quantity of stored collect information for given regency
     */
    public int getCollectsSize(int regency) {

        HashSet<SignedObject> c = collects.get(regency);
        return c == null ? 0 : c.size();
    }

    /**
     * Get the set of collects related to a regency
     * @param regency Regency for collects
     * @return a set of collect data
     */
    public HashSet<SignedObject> getCollects(int regency) {
        return collects.get(regency);
    }

    /**
     * Defines the set of collects related to a regency
     * @param regency Regency for the last eid info
     * @param colls a set of collect data
     */
    public void setCollects(int regency, HashSet<SignedObject> colls) {

        collects.put(regency, colls);
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
        
        HashSet<Integer> timestamps = new HashSet<Integer>();
        HashSet<byte[]> values = new HashSet<byte[]>();

        for (CollectData c : collects) { // organize all existing timestamps and values separately
            
            timestamps.add(c.getQuorumWeaks().getRound()); //store timestamp received from a Byzatine quorum of WEAKS
            
            // store value received from a Byzantine quorum of WEAKS, unless it is an empty value
            if (!Arrays.equals(c.getQuorumWeaks().getValue(), new byte[0])) {
                boolean insert = true; // this loop avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWeaks().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWeaks().getValue());
            }
            for (TimestampValuePair rv : c.getWriteSet()) { // store all timestamps and written values
                timestamps.add(rv.getRound());

                boolean insert = true; // this loop avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, rv.getHashedValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(rv.getHashedValue());
            }

        }

        // after having organized all timestamps and values, properly apply the predicate
        for (int r : timestamps) {
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
     * @param timestamp the timestamp to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean binds(int timestamp, byte[] value, HashSet<CollectData> collects) {

        return (value != null && collects != null && collects.size() > (SVManager.getCurrentViewN() - SVManager.getCurrentViewF()))
                && quorumHighest(timestamp, value, collects) && certifiedValue(timestamp, value, collects);
    }

    /**
     * Return a value that is "bind", that is different from null, and
     * with a timestamp greater or equal to zero
     * @param collects Set of collects from which to determine the value
     * @return The bind value
     */
    public byte[] getBindValue(HashSet<CollectData> collects) {

        if (collects == null) return null;

        HashSet<Integer> timestamps = new HashSet<Integer>();
        HashSet<byte[]> values = new HashSet<byte[]>();

        for (CollectData c : collects) { // organize all existing timestamps and values separately

            timestamps.add(c.getQuorumWeaks().getRound()); //store round received from a Byzantine quorum of weaks
            
            // store value received from a Byzantine quorum of weaks, unless it is an empty value
            if (!Arrays.equals(c.getQuorumWeaks().getValue(), new byte[0])) {
                boolean insert = true; // this loops avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWeaks().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWeaks().getValue());
            }
            for (TimestampValuePair rv : c.getWriteSet()) { // store all timestamps and written values
                timestamps.add(rv.getRound());

                boolean insert = true; // this loops avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, rv.getHashedValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(rv.getHashedValue());
            }

        }

        // after having organized all timestamps and values, properly apply the predicate
        for (int r : timestamps) {
            for (byte[] v : values) {

                if (r >= 0 && binds(r, v, collects)) { // do we have a value that satisfys the predicate?
                    
                    // as we are handling hashes, we have to find the original value
                    for (CollectData c : collects) {
                        for (TimestampValuePair rv : c.getWriteSet()) {

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

        if (collects.size() >= (SVManager.getCurrentViewN() - SVManager.getCurrentViewF())) {


            for (CollectData c : collects) {

                if (c.getQuorumWeaks().getRound() == 0) count++;
            }
        }
        else return false;

        if(SVManager.getStaticConf().isBFT()) {
            unbound = count > ((SVManager.getCurrentViewN() + SVManager.getCurrentViewF()) / 2);
        }
        else {
        	unbound = count > ((SVManager.getCurrentViewN()) / 2);
        }
        return unbound;
        
    }

    /**
     * The predicate "quorumHighest". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param timestamp the timestamp to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean quorumHighest(int timestamp, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean appears = false;
        boolean quorum = false;

        for (CollectData c : collects) {

            if (c.getQuorumWeaks().getRound() == timestamp && Arrays.equals(value, c.getQuorumWeaks().getValue())) {

                appears = true;
                break;
            }
        }

        int count = 0;
        for (CollectData c : collects) {

            if ((c.getQuorumWeaks().getRound() < timestamp)
                    || (c.getQuorumWeaks().getRound() == timestamp && Arrays.equals(value, c.getQuorumWeaks().getValue())))
                        count++;

        }

        if(SVManager.getStaticConf().isBFT()) {
            quorum = count > ((SVManager.getCurrentViewN() + SVManager.getCurrentViewF()) / 2);
        }
        else {
            quorum = count > ((SVManager.getCurrentViewN())/2);
        }      
        return appears && quorum;
    }

    /**
     * The predicate "certifiedValue". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param timestamp the timestamp to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return see page 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean certifiedValue(int timestamp, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean certified = false;

        int count = 0;
        for (CollectData c : collects) {

            for (TimestampValuePair pv : c.getWriteSet()) {

                if (pv.getRound() >= timestamp && Arrays.equals(value, pv.getHashedValue()))
                    count++;
            }

        }

        if(SVManager.getStaticConf().isBFT()) {
            certified = count > SVManager.getCurrentViewF();
        } else {
            certified = count > 0;
        }
        return certified;
    }

    /**
     * Fetchs a set of correctly signed and normalized collect data structures
     * @param regency the regency from which the collects were stored
     * @param eid the eid to which to normalize the collects
     * @return a set of correctly signed and normalized collect data structures
     */
    public HashSet<CollectData> selectCollects(int regency, int eid) {

        HashSet<SignedObject> c = collects.get(regency);

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

        // if there are collects refering to other consensus instances, lets assume that they are still at round zero of the consensus we want
        for (CollectData c : collects) {

            if (c.getEid() == eid) {
                result.add(c);
            }
            else {
                result.add(new CollectData(c.getPid(), eid, new TimestampValuePair(0, new byte[0]), new HashSet<TimestampValuePair>()));
            }

        }

        // calculate hash of the values in the write set
        for (CollectData c : result) {

            for (TimestampValuePair rv : c.getWriteSet()) {

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

            //TODO: CHECK OF THE PROOF IS MISSING!!!!
            if (tomLayer.reconfManager.getStaticConf().isBFT() && hasValidProof(l) && l.getEid() > highest.getEid()) 
                    highest = l;
            else if(l.getEid() > highest.getEid()){
                    highest = l;
             }
        }

        return highest;
    }
    
    // verifies is a proof associated with a decided value is valid
    private boolean hasValidProof(LastEidData led) {

        byte[] hashedValue = md.digest(led.getEidDecision());
        Set<PaxosMessage> PaxosMessages = led.getEidProof();
        int myId = tomLayer.reconfManager.getStaticConf().getProcessId();
        int certificate = (2*tomLayer.reconfManager.getCurrentViewF()) + 1;
        int countValid = 0;
            
        for (PaxosMessage paxosMsg : PaxosMessages) {
            
            HashMap<Integer, byte[]> macVector = (HashMap<Integer, byte[]>) paxosMsg.getMACVector();
                               
            byte[] recvMAC = macVector.get(myId);

            PaxosMessage pm = new PaxosMessage(MessageFactory.STRONG,paxosMsg.getNumber(),
                    paxosMsg.getRound(), paxosMsg.getSender(), paxosMsg.getValue());

            ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
            try {
                new ObjectOutputStream(bOut).writeObject(pm);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            byte[] data = bOut.toByteArray();
            byte[] hash = tomLayer.computeHash(data);
            byte[] myMAC = null;
                
            SecretKeySpec key = new SecretKeySpec(tomLayer.getCommunication().getServersConn().getSecretKey(paxosMsg.getSender()).getEncoded(), "DES");
                
            try {
                this.cipher.init(Cipher.ENCRYPT_MODE, key);                   
                myMAC = this.cipher.doFinal(hash);
            } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException ex) {
                ex.printStackTrace();
            }
            
            if (recvMAC != null && myMAC != null && Arrays.equals(recvMAC, myMAC) &&
                    Arrays.equals(paxosMsg.getValue(), hashedValue) &&
                    paxosMsg.getNumber() == led.getEid()) {
                
                countValid++;
            }
        }
        
        return countValid >= certificate;
    }

    /**
     * Returns the value of the specified last eid for a given regency
     * @param regency the related regency
     * @param eid the last eid
     * @return null if there is no such eid or is invalid, otherwise returns the value
     */
    public byte[] getLastEidValue(int regency, int eid) {

        HashSet<LastEidData> lasts = lastEids.get(regency);

        if (lasts == null) return null;

        byte[] result = null;

        for (LastEidData l : lasts) {

            if (l.getEid() == eid) {

                //TODO: CHECK OF THE PROOF IS MISSING!!!!
                result = l.getEidDecision();
                break;
            }
        }

        return result;
    }
}
