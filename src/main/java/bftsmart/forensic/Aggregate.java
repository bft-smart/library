package bftsmart.forensic;

import java.io.Serializable;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import bftsmart.consensus.messages.ConsensusMessage;

public class Aggregate implements Serializable {

    private HashSet<Integer> senders; // set of all senders
    private HashMap<Integer, ConsensusMessage> agg; // map of senders to proof
    private boolean isWrite;
    private byte[] value; // this is the proposed value by the Quorum (this will be the same for every replica in the quorum)

    public Aggregate(Set<ConsensusMessage> cm, boolean isWrite) {
        this.senders = new HashSet<>();
        this.agg = new HashMap<>();
        for (ConsensusMessage msg : cm) {
            int sender = msg.getSender();
            senders.add(sender);
            agg.put(sender, msg);
        }
        this.isWrite = isWrite;
        this.value = cm.iterator().next().getValue();
    }

    /*************************** DEBUG METHODS *******************************/

    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (isWrite) {
            builder.append("########## WRITE Aggregate ##########\nsenders\tproof\n");
        } else {
            builder.append("########## ACCEPT Aggregate ##########\nsenders\tproof\n");
        }

        for (Integer id : senders) {
            builder.append(id + "\t" + Base64.getEncoder().encodeToString((byte[]) agg.get(id).getProof()) + "\n");
        }

        builder.append("value = " + Base64.getEncoder().encodeToString(value));

        return builder.toString();
    }

    public HashSet<Integer> get_senders() {
        return senders;
    }

    public HashMap<Integer, ConsensusMessage> getProofs() {
        return agg;
    }

    public byte[] getValue(){
        return value;
    }
}
