package bftsmart.forensic;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.tom.util.TOMUtil;

public class AuditTester {

    private HashMap<Integer, KeyPair> keypair;
    private boolean verbose = false;
    private Auditor auditor;

    public AuditTester(int replicasNumber) {
        this.keypair = new HashMap<>(replicasNumber);
        generateKeyPairs(replicasNumber);
        HashMap<Integer, PublicKey> pubkeys = new HashMap<>();
        for (int id : keypair.keySet()) {
            pubkeys.put(id, keypair.get(id).getPublic());
        }
        this.auditor = new Auditor(pubkeys);
    }

    private void SameViewNoConflict() {

        byte[] value = new byte[64]; // random value to be proposed
        Random gen = new Random();
        gen.nextBytes(value);

        /////// replica 1 aggregate
        AuditStorage local_storage = new AuditStorage();
        local_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(0, createAggregate(0, new int[] { 0, 1, 3 }, value, false));
        local_storage.addWriteAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, false));
        local_storage.addWriteAggregate(2, createAggregate(2, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(2, createAggregate(2, new int[] { 0, 1, 3 }, value, false));

        ///////////////////////////

        /////// replica 2 aggregate
        AuditStorage received_storage = new AuditStorage();
        received_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 2, 3 }, value, true));
        received_storage.addAcceptAggregate(0, createAggregate(0, new int[] { 0, 2, 3 }, value, false));
        received_storage.addWriteAggregate(1, createAggregate(1, new int[] { 0, 2, 3 }, value, true));
        received_storage.addAcceptAggregate(1, createAggregate(1, new int[] { 0, 2, 3 }, value, false));
        received_storage.addWriteAggregate(2, createAggregate(2, new int[] { 0, 2, 3 }, value, true));
        received_storage.addAcceptAggregate(2, createAggregate(2, new int[] { 0, 2, 3 }, value, false));

        ///////////////////////////

        AuditResult result = auditor.audit(local_storage, received_storage);
        int[] faulty = result.getReplicasArray();

        if (verbose)
            printVerbose(local_storage, received_storage, faulty);

        // test if there was no conflict found
        assertEquals(new int[] {}, faulty);
    }

    private void SameViewConflict() {

        byte[] value = new byte[64]; // random value to be proposed
        byte[] conflict_value = new byte[64]; // random value to be proposed
        Random gen = new Random();
        gen.nextBytes(value);
        gen.nextBytes(conflict_value);

        /////// replica 1 aggregate
        AuditStorage local_storage = new AuditStorage();
        local_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(0, createAggregate(0, new int[] { 0, 1, 3 }, value, false));
        local_storage.addWriteAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, false));
        local_storage.addWriteAggregate(2, createAggregate(2, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(3, createAggregate(2, new int[] { 0, 1, 3 }, value, false));

        ///////////////////////////

        /////// replica 2 aggregate
        AuditStorage received_storage = new AuditStorage();
        received_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 2, 3 }, value, true));
        received_storage.addAcceptAggregate(0, createAggregate(0, new int[] { 0, 2, 3 }, value, false));
        received_storage.addWriteAggregate(1, createAggregate(1, new int[] { 0, 2, 3 }, conflict_value, true));
        received_storage.addAcceptAggregate(1, createAggregate(1, new int[] { 0, 2, 3 }, conflict_value, false));
        received_storage.addWriteAggregate(2, createAggregate(2, new int[] { 0, 2, 3 }, value, true));
        received_storage.addAcceptAggregate(2, createAggregate(2, new int[] { 0, 2, 3 }, value, false));

        ///////////////////////////

        AuditResult result = auditor.audit(local_storage, received_storage);
        int[] faulty = result.getReplicasArray();

        if (verbose)
            printVerbose(local_storage, received_storage, faulty);

        // test if there was conflict found
        assertEquals(new int[] { 0, 3 }, faulty);
    }

    private void WriteAcceptConflict() {
        byte[] value = new byte[64]; // random value to be proposed
        byte[] conflict_value = new byte[64]; // random value to be proposed
        Random gen = new Random();
        gen.nextBytes(value);
        gen.nextBytes(conflict_value);

        /////// replica 3 aggregate
        AuditStorage local_storage = new AuditStorage();
        // local_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 1, 3 },
        // conflict_value, true));
        local_storage.addAcceptAggregate(0, createAggregate(0, new int[] { 0, 1, 3 }, conflict_value, false));
        local_storage.addWriteAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(1, createAggregate(1, new int[] { 0, 1, 3 }, value, false));
        local_storage.addWriteAggregate(2, createAggregate(2, new int[] { 0, 1, 3 }, value, true));
        local_storage.addAcceptAggregate(3, createAggregate(2, new int[] { 0, 1, 3 }, value, false));

        ///////////////////////////

        /////// replica 2 aggregate
        AuditStorage received_storage = new AuditStorage();
        received_storage.addWriteAggregate(0, createAggregate(0, new int[] { 0, 1, 2 }, value, true));

        ///////////////////////////

        AuditResult result = auditor.audit(local_storage, received_storage);
        int[] faulty = result.getReplicasArray();

        if (verbose)
            printVerbose(local_storage, received_storage, faulty);

        // test if there was conflict found
        assertEquals(new int[] { 0, 1 }, faulty);
    }

    private void printVerbose(AuditStorage local_storage, AuditStorage received_storage, int[] faulty) {
        System.out.println("local storage: " + local_storage);
        System.out.println("Received storage: " + received_storage);
        System.out.println("Faulty replicas = " + Arrays.toString(faulty));
    }

    private Aggregate createAggregate(int consensus_id, int[] replicas, byte[] value, boolean isWrite) {

        Set<ConsensusMessage> setcm = new HashSet<>();
        setcm.add(createConsensusMessage(consensus_id, replicas[0], value));
        setcm.add(createConsensusMessage(consensus_id, replicas[1], value));
        setcm.add(createConsensusMessage(consensus_id, replicas[2], value));

        return new Aggregate(setcm, isWrite);
    }

    private ConsensusMessage createConsensusMessage(int consensus_id, int sender_id, byte[] value) {
        ConsensusMessage cm = new ConsensusMessage(0, consensus_id, 0, sender_id, value);
        insertProof(cm);
        return cm;
    }

    private void insertProof(ConsensusMessage cm) {
        try {
            byte[] data = TOMUtil.getBytes(cm);
            PrivateKey privateKey = keypair.get(cm.getSender()).getPrivate();
            Signature sign = Signature.getInstance("SHA256withDSA");
            sign.initSign(privateKey);
            sign.update(data);
            byte[] signature = sign.sign();
            cm.setProof(signature);
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("Error inserting Proof");
        }
    }

    private void generateKeyPairs(int N) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
            kpg.initialize(2048);
            for (int i = 0; i < N; i++) {
                KeyPair kp = kpg.genKeyPair();
                this.keypair.put(i, kp);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace(); // should never happen
        }

        // PublicKey publicKey = kp.getPublic();
        // PrivateKey privateKey = kp.getPrivate();
    }

    public static void main(String[] args) {
        int N = 4;
        AuditTester tester = new AuditTester(N);

        System.out.println("Same view no conflict:");
        tester.SameViewNoConflict();

        System.out.println("\nSame view conflict:");
        tester.SameViewConflict();

        System.out.println("\nWrite Accept conflict:");
        tester.WriteAcceptConflict();
    }

    ///// Test Code

    private void assertEquals(int[] expected, int[] actual) {
        if (Arrays.equals(expected, actual)) {
            System.out.println("success");
        } else {
            System.out.println("expected: " + Arrays.toString(expected) + ", result " + Arrays.toString(actual));
        }
    }
}
