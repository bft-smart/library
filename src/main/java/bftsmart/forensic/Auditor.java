package bftsmart.forensic;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.util.Configuration;
import bftsmart.tom.util.TOMUtil;

/**
 * Class responsible for receiving audit storages and check for conflict
 */
public class Auditor {

    private boolean verbose = false;
    private Map<Integer, PublicKey> keys; // <process, Configuration>

    public Auditor() {
        keys = new HashMap<>();
    }

    public Auditor(Map<Integer, PublicKey> keys) {
        this.keys = keys;
    }

    /**
     * Checks for conflict in a list of storages
     * 
     * @param storages list of storages
     * @return audit result with conflicts if found
     */
    public AuditResult audit(List<AuditStorage> storages) {

        AuditResult result = new AuditResult();

        // <consensus id, <sender, proof>>
        Map<Integer, Map<Integer, ConsensusMessage>> acceptMap = new HashMap<>();
        Map<Integer, Map<Integer, ConsensusMessage>> writetMap = new HashMap<>();

        for (AuditStorage storage : storages) { // iterate over all responses

            // System.out.println(storage.toString());

            // first check for problems in conflicting accepts
            CheckAggregate(storage.getAcceptAggregate(), result, acceptMap, 0);

            // second check for problems in conflicting Writes
            CheckAggregate(storage.getWriteAggregate(), result, writetMap, 0);

        }
        if (result.conflictFound()) {
            System.out.println(result);
        }
        return result;
    }

    // public AuditResult audit(List<AuditResponse> responses) {

    // AuditResult result = new AuditResult();
    // for (AuditResponse res : responses) {
    // System.out.println("Storage from " + res.getSender() + ":" +
    // res.getStorage());
    // }

    // // <consensus id, <sender, proof>>
    // Map<Integer, Map<Integer, ConsensusMessage>> acceptMap = new HashMap<>();
    // Map<Integer, Map<Integer, ConsensusMessage>> writetMap = new HashMap<>();

    // for (AuditResponse auditResponse : responses) { // iterate over all responses
    // AuditStorage storage = auditResponse.getStorage();

    // // System.out.println(storage.toString());

    // // first check for problems in conflicting accepts
    // CheckAggregate(storage.getAcceptAggregate(), result, acceptMap);

    // // second check for problems in conflicting Writes
    // CheckAggregate(storage.getWriteAggregate(), result, writetMap);

    // }
    // // System.out.println("Number of faulty replicas = " + faulty.size());
    // if (result.conflictFound()) {
    // System.out.println(result);
    // }
    // return result;
    // }

    /**
     * Checks for conflict between two storages
     * 
     * @param local_storage    local storage
     * @param received_storage received storage
     * @param minCid           lowest consensus id needed to be checked
     * @return audit result with conflicts if found
     */
    public AuditResult audit(AuditStorage local_storage, AuditStorage received_storage, int minCid) {
        AuditResult result = new AuditResult();
        // fakeResponses(local_storage, received_storage); // used for testing coment
        // otherwise
        // System.out.println("Local storage: " + local_storage);
        // System.out.println("Received storage: " + received_storage);

        // <consensus id, <sender, proof>>
        Map<Integer, Map<Integer, ConsensusMessage>> acceptMap = new HashMap<>();
        Map<Integer, Map<Integer, ConsensusMessage>> writetMap = new HashMap<>();

        // first check for problems in conflicting Writes
        CheckAggregate(local_storage.getWriteAggregate(), result, writetMap, minCid);
        CheckAggregate(received_storage.getWriteAggregate(), result, writetMap, minCid);

        // second check for problems in conflicting Accepts
        CheckAggregate(local_storage.getAcceptAggregate(), result, acceptMap, minCid);
        CheckAggregate(received_storage.getAcceptAggregate(), result, acceptMap, minCid);

        // third check for writes conflicting with accepts
        CheckAggregate(local_storage.getWriteAggregate(), result, acceptMap, minCid);
        CheckAggregate(received_storage.getWriteAggregate(), result, acceptMap, minCid);

        return result;
    }

    /**
     * Checks for conflict between two storages
     * 
     * @param local_storage    local storage
     * @param received_storage received storage
     * @return audit result with conflicts if found
     */
    public AuditResult audit(AuditStorage local_storage, AuditStorage received_storage) {
        return audit(local_storage, received_storage, 0);
    }

    /**
     * Checks for conflict inside a single storage
     * 
     * @param storage storage
     * @return audit result with conflicts if found
     */
    public AuditResult audit(AuditStorage storage) {
        AuditResult result = new AuditResult();

        Map<Integer, Map<Integer, ConsensusMessage>> acceptMap = new HashMap<>();
        Map<Integer, Map<Integer, ConsensusMessage>> writetMap = new HashMap<>();

        // first check for problems in conflicting Writes
        CheckAggregate(storage.getWriteAggregate(), result, writetMap, 0);

        // second check for problems in conflicting Accepts
        CheckAggregate(storage.getAcceptAggregate(), result, acceptMap, 0);

        // third check for writes conflicting with accepts
        CheckAggregate(storage.getWriteAggregate(), result, acceptMap, 0);

        return result;
    }

    /**
     * Checks several aggregates and fills proof map and audit result
     * 
     * @param aggregates map with aggregate from specific consensus ids (<consensus
     *                   id, aggregate>)
     * @param result     audit result to be filled
     * @param proofMap   map filled with information from already checked aggregates
     *                   (<consensus id, <sender, proof>>)
     * @param minCid     lowest consensus id needed to be checked
     */
    private void CheckAggregate(Map<Integer, Aggregate> aggregates, AuditResult result,
            Map<Integer, Map<Integer, ConsensusMessage>> proofMap, int minCid) {

        for (Integer consensusId : aggregates.keySet()) {
            if (consensusId < minCid)
                continue;
            if (proofMap.get(consensusId) == null) {
                proofMap.put(consensusId, new HashMap<>());
            }
            // check all aggregates
            Aggregate agg = aggregates.get(consensusId);

            // validade signature (comment for testing)
            boolean isValid = validSignature(agg);
            if (!isValid) {
                // TODO the this aggregate is faulty should not be tested
                System.out.println("Aggregate Signatures Invalid!!");
                continue; // skip to next iteration
            }

            HashMap<Integer, ConsensusMessage> sender_proof = agg.getProofs();

            // for all senders check all proofs and add to the respective consensus id
            for (Integer sender : agg.get_senders()) {
                if (result.getReplicas().contains(sender))
                    continue; // skip to next iteration

                ConsensusMessage cm = sender_proof.get(sender);

                // audit
                // check if there is a different value for the same sender
                if (proofMap.get(consensusId).get(sender) == null) { // if proof is not saved
                    proofMap.get(consensusId).put(sender, cm); // save proof
                } else if (!Arrays.equals((byte[]) proofMap.get(consensusId).get(sender).getValue(),
                        (byte[]) cm.getValue())) {

                    // System.out.println(Arrays.toString((byte[])
                    // proofMap.get(consensusId).get(sender).getValue()));
                    // System.out.println(Arrays.toString((byte[]) cm.getValue()));
                    // if saved value is different than current value, replica is faulty
                    result.addReplica(sender);
                    if (consensusId < result.getFaultyView()) {
                        result.setFaultyView(consensusId);
                    }
                }
            }
        }
    }

    /**
     * method used for testing detection of faulty replicas
     * 
     * @param local
     * @param received
     */
    private void fakeResponses(AuditStorage local, AuditStorage received) {

        boolean mock = true;
        if (mock) {
            changeValueInStorage(local, received, 0, "clgckjeccdvdeuk".getBytes());
        }
    }

    /**
     * This method changes the value proposed in one of the aggregates
     * Made to test if the detection of faulty replicas is correct
     * 
     * @param responses    responses
     * @param concensus_id consensus id to change value
     * @param new_value    new value to change
     */
    private void changeValueInStorage(AuditStorage local, AuditStorage received, int concensus_id, byte[] new_value) {

        Aggregate agg = local.getWriteAggregate().get(concensus_id);
        for (int i = 0; i < 3; i++) {
            ConsensusMessage cm = agg.getProofs().get(i);
            if (cm == null)
                continue;
            cm.setValue(new_value);
            cm.setProof("FAKEPROOF".getBytes());
        }

        agg = local.getAcceptAggregate().get(concensus_id);
        for (int i = 0; i < 3; i++) {
            ConsensusMessage cm = agg.getProofs().get(i);
            if (cm == null)
                continue;
            cm.setValue(new_value);
            cm.setProof("FAKEPROOF".getBytes());
        }

        // System.out.println("\n\nAfter alterarion");
        // System.out.println("Local storage: " + local);
        // System.out.println("Received storage: " + received);
    }

    /**
     * This method checks is an Aggregate has valid signatures
     * If a signature does not decrypt to the values saved in the Aggregate
     * the aggregate is invalid
     * 
     * @param agg Aggregate to check
     * @return true if Aggregate proofs corresponds to the correct value, false
     *         otherwise
     */
    public boolean validSignature(Aggregate agg) {
        for (Integer sender_id : agg.get_senders()) {
            if (keys.get(sender_id) == null)
                keys.put(sender_id, new Configuration(sender_id, null).getPublicKey());

            ConsensusMessage cm = agg.getProofs().get(sender_id);
            byte[] signature = (byte[]) cm.getProof();
            cm.setProof(null); // need to remove proof because signature was calculated without the proof
            byte[] data = TOMUtil.getBytes(cm);

            // Signature verification throws expection... // TODO
            // if (!TOMUtil.verifySignature(keys.get(sender_id), data, signature)) {
            // System.out.println("Signature incorrect");
            // return false;
            // }
        }
        return true;
    }
}
