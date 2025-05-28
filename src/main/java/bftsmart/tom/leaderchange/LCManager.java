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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SignedObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.consensus.TimestampValuePair;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;

/**
 *
 * This class implements a manager of information related to the leader change protocol
 * It also implements some predicates and methods necessary for the protocol in accordance
 * to Cachin's 'Yet Another Visit to Paxos' (April 2011).
 * 
 * @author Joao Sousa
 */
public class LCManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    //timestamp info
    private int lastreg;
    private int nextreg;

    //requests that timed out
    private List<TOMMessage> currentRequestTimedOut = null;

    //requests received in STOP messages
    private List<TOMMessage> requestsFromSTOP = null;
    
    //data structures for info in stop, sync and catch-up messages
    private HashMap<Integer,HashSet<Integer>> stops;
    private HashMap<Integer,HashSet<CertifiedDecision>> lastCIDs;
    private HashMap<Integer,HashSet<SignedObject>> collects;

    //stuff from the TOM layer that this object needss
    private ServerViewController SVController;
    private MessageDigest md;
    private TOMLayer tomLayer;
    
    private int currentLeader;
    //private Cipher cipher;
    
    /**
     * Constructor
     *
     * @param tomLayer The TOM layer
     * @param SVController The reconfiguration manager from TOM layer
     * @param md The message digest engine from TOM layer
     */
    public LCManager(TOMLayer tomLayer,ServerViewController SVController, MessageDigest md) {
        this.tomLayer = tomLayer;
        this.lastreg = 0;
        this.nextreg = 0;
        this.currentLeader = 0;

        this.stops = new HashMap<>();
        this.lastCIDs = new HashMap<>();
        this.collects = new HashMap<>();

        this.SVController = SVController;
        this.md = md;

    }
    
    /**
     * Deterministically elects a new leader, based current leader and membership
     * 
     * @return The new leader
     */
    public int getNewLeader() {

        int[] proc = SVController.getCurrentViewProcesses();
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
        } while(!SVController.isCurrentViewMember(currentLeader));
        
        return currentLeader;
    }
    
    /**
     * Informs the object of who is the current leader
     * @param leader The current leader
     */
    public void setNewLeader(int leader) {
        currentLeader = leader;
    }
    
    /**
     * This is meant to keep track of timed out requests in this replica
     *
     * @param currentRequestTimedOut Timed out requests in this replica
     */
    public void setCurrentRequestTimedOut(List<TOMMessage> currentRequestTimedOut) {
        this.currentRequestTimedOut = currentRequestTimedOut;
    }

    /**
     * Get the timed out requests in this replica
     * @return timed out requests in this replica
     */
    public List<TOMMessage> getCurrentRequestTimedOut() {
        return currentRequestTimedOut;
    }
    
    /**
     * Discard timed out requests in this replica
     */
    public void clearCurrentRequestTimedOut() {
        if (currentRequestTimedOut != null) currentRequestTimedOut.clear();
        currentRequestTimedOut = null;
    }

    /**
     * This is meant to keep track of requests received in STOP messages
     *
     * @param requestsFromSTOP Requests received in a STOP message
     */
    public void addRequestsFromSTOP(TOMMessage[] requestsFromSTOP) {
        if (this.requestsFromSTOP == null)
            this.requestsFromSTOP = new LinkedList<>();
        
        for (TOMMessage m : requestsFromSTOP)
            this.requestsFromSTOP.add(m);
    }

    /**
     * Get the requests received in STOP messages
     * @return requests received in STOP messages
     */
    public List<TOMMessage> getRequestsFromSTOP() {
        return requestsFromSTOP;
    }
    
    /**
     * Discard requests received in STOP messages
     */    
    public void clearRequestsFromSTOP() {
        if (requestsFromSTOP != null) requestsFromSTOP.clear();
        requestsFromSTOP = null;
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
     * @param nextreg next regency
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
     * @param regency timestamp up to which to discard messages
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
     * Keep last CID from an incoming SYNC message
     * @param regency the current regency
     * @param lastCID the last CID data
     */
    public void addLastCID(int regency, CertifiedDecision lastCID) {

        HashSet<CertifiedDecision> last = lastCIDs.get(regency);
        if (last == null) last = new HashSet<CertifiedDecision>();
        last.add(lastCID);
        lastCIDs.put(regency, last);
    }

    /**
     * Discard last CID information up to the specified regency
     * @param regency Regency up to which to discard information
     */
    public void removeLastCIDs(int regency) {
        Integer[] keys = new Integer[lastCIDs.keySet().size()];
        lastCIDs.keySet().toArray(keys);

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] <= regency) lastCIDs.remove(keys[i]);
        }
    }

    /**
     * Get the quantity of stored last CID information
     * @param regency regency to be considered
     * @return quantity of stored last CID  information for given regency
     */
    public int getLastCIDsSize(int regency) {
        HashSet<CertifiedDecision> last = lastCIDs.get(regency);
        return last == null ? 0 : last.size();
    }

    /**
     * Get the set of last CIDs related to a regency
     * @param regency Regency for the last CID info
     * @return a set of last CID data
     */
    public HashSet<CertifiedDecision> getLastCIDs(int regency) {
        return lastCIDs.get(regency);
    }

    /**
     * Defines the set of last CIDs related to a regency
     * @param regency Regency for the last CID info
     * @param lasts a set of last CID data
     */
    public void setLastCIDs(int regency, HashSet<CertifiedDecision> lasts) {

        lastCIDs.put(regency, lasts);
    }

    /**
     * Keep collect from an incoming SYNC message
     * @param regency the current regency
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
     * @param regency Regency for the last CID info
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
     * @return See Cachin's 'Yet Another Visit to Paxos' (April 2011), page 11
     * 
     * In addition, see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean sound(HashSet<CollectData> collects) {

        logger.debug("I collected the context from " + collects.size() + " replicas");
        
        if (collects == null) return false;
        
        HashSet<Integer> timestamps = new HashSet<Integer>();
        HashSet<byte[]> values = new HashSet<byte[]>();

        for (CollectData c : collects) { // organize all existing timestamps and values separately
            
            logger.debug("Context for replica "+c.getPid()+": CID["+c.getCid()+"] WRITESET["+c.getWriteSet()+"] (VALTS,VAL)[" + c.getQuorumWrites() +"]");
            
            timestamps.add(c.getQuorumWrites().getTimestamp()); //store timestamp received from a Byzatine quorum of WRITES
            
            // store value received from a Byzantine quorum of WRITES, unless it is an empty value
            if (!Arrays.equals(c.getQuorumWrites().getValue(), new byte[0])) {
                boolean insert = true; // this loop avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWrites().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWrites().getValue());
            }
            for (TimestampValuePair rv : c.getWriteSet()) { // store all timestamps and written values
                timestamps.add(rv.getTimestamp());

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

        logger.debug("number of timestamps: "+timestamps.size());
        logger.debug("number of values: "+values.size());

        // after having organized all timestamps and values, properly apply the predicate
        for (int r : timestamps) {
            for (byte[] v : values) {

                logger.debug("testing predicate BIND for timestamp/value pair (" + r + " , " + Arrays.toString(v) + ")");
                if (binds(r, v, collects)) {

                    logger.debug("Predicate BIND is true for timestamp/value pair (" + r + " , " + Arrays.toString(v) + ")");
                    logger.debug("Predicate SOUND is true for the for context collected from N-F replicas");
                    return true;
                }
            }
        }

        logger.debug("No timestamp/value pair passed on the BIND predicate");
        
        boolean unbound = unbound(collects);
        
        if (unbound) {
            logger.debug("Predicate UNBOUND is true for N-F replicas");
            logger.debug("Predicate SOUND is true for the for context collected from N-F replicas");
        }

        return unbound;
    }

    /**
     * The predicate "binds". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param timestamp the timestamp to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return See Cachin's 'Yet Another Visit to Paxos' (April 2011), page 11
     * 
     * In addition, see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean binds(int timestamp, byte[] value, HashSet<CollectData> collects) {

        if (value == null || collects == null) {
            logger.debug("Received null objects, returning false");
            return false;
        }
        
        if (!(collects.size() >= (SVController.getCurrentViewN() - SVController.getCurrentViewF()))) {
            logger.debug("Less than N-F contexts collected from replicas, returning false");
            return false;
        }

        return (quorumHighest(timestamp, value, collects) && certifiedValue(timestamp, value, collects));

    }

    /**
     * Return a value that is "bind", that is different from null, and
     * with a timestamp greater or equal to zero
     * @param collects Set of collects from which to determine the value
     * @return The bind value
     * 
     * See Cachin's 'Yet Another Visit to Paxos' (April 2011), page 11
     * Also, see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public byte[] getBindValue(HashSet<CollectData> collects) {

        if (collects == null) return null;

        HashSet<Integer> timestamps = new HashSet<>();
        HashSet<byte[]> values = new HashSet<>();

        for (CollectData c : collects) { // organize all existing timestamps and values separately

            timestamps.add(c.getQuorumWrites().getTimestamp()); //store timestamp received from a Byzantine quorum of writes
            
            // store value received from a Byzantine quorum of writes, unless it is an empty value
            if (!Arrays.equals(c.getQuorumWrites().getValue(), new byte[0])) {
                boolean insert = true; // this loops avoids putting duplicated values in the set
                for (byte[] b : values) {

                    if (Arrays.equals(b, c.getQuorumWrites().getValue())) {
                        insert = false;
                        break;
                    }
                }
                if (insert) values.add(c.getQuorumWrites().getValue());
            }
            for (TimestampValuePair rv : c.getWriteSet()) { // store all timestamps and written values
                timestamps.add(rv.getTimestamp());

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

                            if (rv.getValue() != null && Arrays.equals(v, rv.getHashedValue())) {
                                return rv.getValue();
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
     * @return See Cachin's 'Yet Another Visit to Paxos' (April 2011), page 11
     * 
     * In addition, see page 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean unbound(HashSet<CollectData> collects) {

        if (collects == null) return false;

        boolean unbound = false;
        int count = 0;

        if (collects.size() >= (SVController.getCurrentViewN() - SVController.getCurrentViewF())) {


            for (CollectData c : collects) {

                if (c.getQuorumWrites().getTimestamp() == 0) count++;
            }
        }
        else return false;

        if(SVController.getStaticConf().isBFT()) {
            unbound = count > ((SVController.getCurrentViewN() + SVController.getCurrentViewF()) / 2);
        }
        else {
        	unbound = count > ((SVController.getCurrentViewN()) / 2);
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
     * @return See Cachin's 'Yet Another Visit to Paxos' (April 2011), pages 10-11
     * 
     * In addition, see pages 252 and 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean quorumHighest(int timestamp, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean appears = false;
        boolean quorum = false;

        for (CollectData c : collects) {

            if (c.getQuorumWrites().getTimestamp() == timestamp && Arrays.equals(value, c.getQuorumWrites().getValue())) {

                appears = true;
                break;
            }
        }

        if (appears) logger.debug("timestamp/value pair (" + timestamp + " , " + Arrays.toString(value) + ") appears in at least one replica context");
        
        int count = 0;
        for (CollectData c : collects) {

            //bftsmart.tom.util.Logger.println("\t\t[QUORUM HIGHEST] ts' < ts : " + (c.getQuorumWrites().getTimestamp() < timestamp));
            //bftsmart.tom.util.Logger.println("\t\t[QUORUM HIGHEST] ts' = ts && val' = val : " + (c.getQuorumWrites().getTimestamp() == timestamp && Arrays.equals(value, c.getQuorumWrites().getValue())));
            
            if ((c.getQuorumWrites().getTimestamp() < timestamp)
                    || (c.getQuorumWrites().getTimestamp() == timestamp && Arrays.equals(value, c.getQuorumWrites().getValue())))
                        count++;

        }

        if(SVController.getStaticConf().isBFT()) {
            quorum = count > ((SVController.getCurrentViewN() + SVController.getCurrentViewF()) / 2);
        }
        else {
            quorum = count > ((SVController.getCurrentViewN())/2);
        }
        if (quorum)logger.debug("timestamp/value pair (" + timestamp + " , " + Arrays.toString(value) +
                ") has the highest timestamp among a " + (SVController.getStaticConf().isBFT() ? "Byzantine" : "simple") + " quorum of replica contexts");
        return appears && quorum;
    }

    /**
     * The predicate "certifiedValue". This method must received a set of collects that were
     * filtered using the method selectCollects()
     *
     * @param timestamp the timestamp to search for
     * @param value the value to search for
     * @param collects the collect data to which to apply the predicate.
     * @return See Cachin's 'Yet Another Visit to Paxos' (April 2011), page 11
     * 
     * In addition, see page 253 from "Introduction to Reliable and Secure Distributed Programming"
     */
    public boolean certifiedValue(int timestamp, byte[] value, HashSet<CollectData> collects) {

        if (collects == null || value == null) return false;

        boolean certified = false;

        int count = 0;
        for (CollectData c : collects) {

            for (TimestampValuePair pv : c.getWriteSet()) {

//                bftsmart.tom.util.Logger.println("\t\t[CERTIFIED VALUE] " + pv.getTimestamp() + "  >= " + timestamp);
//                bftsmart.tom.util.Logger.println("\t\t[CERTIFIED VALUE] " + Arrays.toString(value) + "  == " + Arrays.toString(pv.getValue()));
                if (pv.getTimestamp() >= timestamp && Arrays.equals(value, pv.getHashedValue()))
                    count++;
            }

        }

        if(SVController.getStaticConf().isBFT()) {
            certified = count > SVController.getCurrentViewF();
        } else {
            certified = count > 0;
        }
        if (certified) logger.debug("timestamp/value pair (" + timestamp + " , " + Arrays.toString(value) +
                ") has been written by at least " + count + " replica(s)");

        return certified;
    }

    /**
     * Fetchs a set of correctly signed and normalized collect data structures
     * @param regency the regency from which the collects were stored
     * @param cid the CID to which to normalize the collects
     * @return a set of correctly signed and normalized collect data structures
     */
    public HashSet<CollectData> selectCollects(int regency, int cid) {

        HashSet<SignedObject> c = collects.get(regency);

        if (c == null) return null;

        return normalizeCollects(getSignedCollects(c), cid, regency);
        
    }

    /**
     * Fetchs a set of correctly signed and normalized collect data structures from the
     * specified original set of collects
     * @param signedObjects original set of signed collects
     * @param cid the CID to which to normalize the collects
     * @return a set of correctly signed and normalized collect data structures
     */
    public HashSet<CollectData> selectCollects(HashSet<SignedObject> signedObjects, int cid, int regency) {

        if (signedObjects == null) return null;

        return normalizeCollects(getSignedCollects(signedObjects), cid, regency);

    }

    // Filters the correctly signed collects
    private HashSet<CollectData> getSignedCollects(HashSet<SignedObject> signedCollects) {

        HashSet<CollectData> colls = new HashSet<CollectData>();
        HashSet<Integer> currentViewMembers = new HashSet<>();
        for (int processId : SVController.getCurrentViewAcceptors()) {
            currentViewMembers.add(processId);
        }

        for (SignedObject so : signedCollects) {

            CollectData c;
            try {
                c = (CollectData) so.getObject();
                int sender = c.getPid();
                if (currentViewMembers.contains(sender) && tomLayer.verifySignature(so, sender)) {
                    colls.add(c);
                }
            } catch (IOException | ClassNotFoundException ex) {
                logger.error("Error processing collect data", ex);
            }
        }

        return colls;

    }

    // Normalizes the set of collects. A set of collects is considered normalized if or when
    // all collects are related to the same CID. This is important because not all replicas
    // may be executing the same CID when tere is a leader change
    private HashSet<CollectData> normalizeCollects(HashSet<CollectData> collects, int cid, int regency) {

        HashSet<CollectData> result = new HashSet<CollectData>();

        // if there are collects refering to other consensus instances, lets assume that they are still at timestamp zero of the consensus we want
        for (CollectData c : collects) {

            if (c.getCid() == cid) {
                if (c.getEts() == regency) {
                    result.add(c);
                }
            }
            else {
                result.add(new CollectData(c.getPid(), cid, regency, new TimestampValuePair(0, new byte[0]), new HashSet<TimestampValuePair>()));
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
     * Gets the highest valid last CID related to the given timestamp
     * @param ts the timestamp
     * @return -1 if there is no such CID, otherwise returns the highest valid last CID
     */
    public CertifiedDecision getHighestLastCID(int ts) {

        CertifiedDecision highest = new CertifiedDecision(-2, -2, null, null);

        HashSet<CertifiedDecision> lasts = lastCIDs.get(ts);

        if (lasts == null) return null;
       
        for (CertifiedDecision l : lasts) {

            //TODO: CHECK OF THE PROOF IS MISSING!!!!
            if (tomLayer.controller.getStaticConf().isBFT() && hasValidProof(l) && l.getCID() > highest.getCID()) 
                    highest = l;
            else if(l.getCID() > highest.getCID()){
                    highest = l;
             }
        }

        return highest;
    }
    
    // verifies is a proof associated with a decided value is valid
    public boolean hasValidProof(CertifiedDecision cDec) {
        
        if (cDec.getCID() == -1) return true; // If the last CID is -1 it means the replica
                                             // did not complete any consensus and cannot have
                                             // any proof
        
        byte[] hashedValue = md.digest(cDec.getDecision());
        Set<ConsensusMessage> ConsensusMessages = cDec.getConsMessages();
        int certificateCurrentView = (2*tomLayer.controller.getCurrentViewF()) + 1;
        int certificateLastView = -1;
        if (tomLayer.controller.getLastView() != null) certificateLastView = (2*tomLayer.controller.getLastView().getF()) + 1;
        int countValid = 0;
        PublicKey pubKey = null;
        
        HashSet<Integer> alreadyCounted = new HashSet<>(); //stores replica IDs that were already counted
            
        for (ConsensusMessage consMsg : ConsensusMessages) {
            
            ConsensusMessage cm = new ConsensusMessage(consMsg.getType(),consMsg.getNumber(),
                    consMsg.getEpoch(), consMsg.getSender(), consMsg.getValue());

            ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
            try {
                new ObjectOutputStream(bOut).writeObject(cm);
            } catch (IOException ex) {
                logger.error("Could not serialize message",ex);
            }

            byte[] data = bOut.toByteArray();

            if (consMsg.getProof() instanceof byte[]) { // certificate is made of signatures
                
                logger.debug("Proof made of Signatures");
                pubKey = SVController.getStaticConf().getPublicKey(consMsg.getSender());
                   
                byte[] signature = (byte[]) consMsg.getProof();
                            
                if (Arrays.equals(consMsg.getValue(), hashedValue) &&
                        TOMUtil.verifySignature(pubKey, data, signature) && !alreadyCounted.contains(consMsg.getSender())) {
                    
                    alreadyCounted.add(consMsg.getSender());
                    countValid++;
                } else {
                    logger.error("Invalid signature in message from " + consMsg.getSender());
                }
   
            } else {
                logger.debug("Proof message is not an instance of byte[].");
            }
        }
        
        // If proofs were made of signatures, use a certificate correspondent to last view
        // otherwise, use certificate for the current view
        // To understand why this is important, check the comments in Acceptor.computeWrite()
                
        if (certificateLastView != -1 && pubKey != null)
            logger.debug("Computing certificate based on previous view");
        
        //return countValid >= certificateCurrentView;
        boolean ret = countValid >=  (certificateLastView != -1 && pubKey != null ? certificateLastView : certificateCurrentView);
        logger.debug("Proof for CID {} is {} ({} valid messages, needed {})",
                cDec.getCID(), (ret ? "valid" : "invalid"), countValid, ( pubKey != null ? certificateLastView : certificateCurrentView));
        return ret;
    }

    /**
     * Returns the value of the specified last CID for a given regency
     * @param regency the related regency
     * @param cid the last CID
     * @return null if there is no such CID or is invalid, otherwise returns the value
     */
    public byte[] getLastCIDValue(int regency, int cid) {

        HashSet<CertifiedDecision> lasts = lastCIDs.get(regency);

        if (lasts == null) return null;

        byte[] result = null;

        for (CertifiedDecision l : lasts) {

            if (l.getCID() == cid) {

                //TODO: CHECK OF THE PROOF IS MISSING!!!!
                result = l.getDecision();
                break;
            }
        }

        return result;
    }
    
    /**
     * Gets the highest ETS associated with a
     * consensus ID from the given collects
     * 
     * @param cid The consensus ID
     * @param collects The collects from the other replicas
     * @return  The highest ETS
     */
    public int getETS(int cid, Set<CollectData> collects) {
        
        int ets = -1;
        int count = 0;
        
        for (CollectData c : collects) {
            
            if (c.getCid() == cid) {
                
                if (c.getEts() > ets) {
                    
                    ets = c.getEts();
                    count = 1;
                } else if (c.getEts() == ets) {
                    count++;
                }
                
            }
        }
        
        return (count > this.SVController.getCurrentViewF() ? ets : -1);
    }
}
