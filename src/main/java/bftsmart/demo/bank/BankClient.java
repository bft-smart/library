package bftsmart.demo.bank;

import bftsmart.tom.ServiceProxy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class Reply {
    boolean ok;
    double value;

    Reply(boolean ok, double value) {
        this.ok = ok;
        this.value = value;
    }
}

public class BankClient {

    private final ServiceProxy proxy;

    public BankClient(int clientId) {
        this.proxy = new ServiceProxy(clientId);
    }

    public Reply deposit(String owner, double amount) throws IOException {
        byte[] reply = proxy.invokeOrdered(buildRequest(BankServer.DEPOSIT, owner, amount));
        return parseReply(reply);
    }

    public Reply withdraw(String owner, double amount) throws IOException {
        byte[] reply = proxy.invokeOrdered(buildRequest(BankServer.WITHDRAW, owner, amount));
        return parseReply(reply);
    }

    public Reply balance(String owner) throws IOException {
        byte[] reply = proxy.invokeUnordered(buildRequest(BankServer.BALANCE, owner, 0.0));
        return parseReply(reply);
    }

    private byte[] buildRequest(int operation, String owner, double amount) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(operation);
        out.writeUTF(owner);
        out.writeDouble(amount);
        return bos.toByteArray();
    }

    private Reply parseReply(byte[] reply) throws IOException {
        if (reply != null) {

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(reply));
            boolean ok = in.readBoolean();
            double balance = in.readDouble();
            return new Reply(ok, balance);
        } else {
            System.out.println("ERROR! Exiting.");
            return new Reply(false, 0);
        }
    }

    public void close() {
        proxy.close();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java BankClient <process id> <owner> <deposit|withdraw|balance> [value]");
            System.exit(-1);
        }

        BankClient client = new BankClient(Integer.parseInt(args[0]));
        String owner = args[1];
        String operation = args[2];
        double amount = args.length > 3 ? Double.parseDouble(args[3]) : 0.0; // ignored for balance operation

        Reply reply = null;
        switch (operation) {
            case "deposit":
                reply = client.deposit(owner, amount);
                break;
            case "withdraw":
                reply = client.withdraw(owner, amount);
                break;
            case "balance":
                reply = client.balance(owner);
                break;
        }

        System.out.printf("Success: %b, Balance: %.2f%n", reply.ok, reply.value);
        // close the client to release resources
        client.close();
    }
}