package bftsmart.demo.bank;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import java.util.HashMap;
import java.util.Map;

public final class BankServer extends DefaultSingleRecoverable {

    private Map<String, Double> accounts = new HashMap<>();

    public static final int DEPOSIT = 1;
    public static final int WITHDRAW = 2;
    public static final int BALANCE = 3;

    public BankServer(int id) {
        new ServiceReplica(id, this, this);
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(command));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos)) {

            int operation = in.readInt();
            String owner = in.readUTF();

            if (operation == BALANCE) {
                double balance = accounts.getOrDefault(owner, 0.0);
                out.writeBoolean(true);
                out.writeDouble(balance);
                System.out.printf("[BALANCE] %s = %.2f%n", owner, balance);
            } else {
                out.writeBoolean(false);
                out.writeDouble(0);
            }
            return bos.toByteArray();

        } catch (IOException ex) {
            System.err.println("Invalid request received!");
            return new byte[0];
        }
    }

    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(command));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos)) {

            int operation = in.readInt();
            String owner = in.readUTF();
            double amount = in.readDouble();

            switch (operation) {
                case DEPOSIT:
                    accounts.put(owner, accounts.getOrDefault(owner, 0.0) + amount);
                    out.writeBoolean(true);
                    out.writeDouble(accounts.get(owner));
                    System.out.printf("[DEPOSIT] %s += %.2f -> %.2f%n", owner, amount, accounts.get(owner));
                    break;
                case WITHDRAW:
                    double balance = accounts.getOrDefault(owner, 0.0);
                    if (balance >= amount) {
                        accounts.put(owner, balance - amount);
                        out.writeBoolean(true);
                        out.writeDouble(accounts.get(owner));
                        System.out.printf("[WITHDRAW] %s -= %.2f -> %.2f%n", owner, amount, accounts.get(owner));
                    } else {
                        out.writeBoolean(false);
                        out.writeDouble(balance);
                        System.out.printf("[WITHDRAW FAILED] %s (%.2f < %.2f)%n", owner, balance,
                                amount);
                    }
                    break;
                default:
                    out.writeBoolean(false);
                    out.writeDouble(0);
            }
            return bos.toByteArray();

        } catch (IOException ex) {
            System.err.println("Invalid request received!");
            return new byte[0];
        }
    }

    @Override
    public byte[] getSnapshot() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(accounts);
            out.flush();
            bos.flush();
            out.close();
            bos.close();
            return bos.toByteArray();
        } catch (IOException ioe) {
            System.err.println("[ERROR] Error serializing state: "
                    + ioe.getMessage());
            return "ERROR".getBytes();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void installSnapshot(byte[] state) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(state);
            ObjectInput in = new ObjectInputStream(bis);
            accounts = (Map<String, Double>) in.readObject();
            in.close();
            bis.close();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[ERROR] Error deserializing state: "
                    + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Use: java BankServer <processId>");
            System.exit(-1);
        }
        new BankServer(Integer.parseInt(args[0]));
    }

}