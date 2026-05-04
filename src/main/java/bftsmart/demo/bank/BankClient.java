package bftsmart.demo.bank;

import bftsmart.tom.ServiceProxy;

import java.io.*;

public class BankClient {

    private final ServiceProxy proxy;

    public BankClient(int clientId) {
        this.proxy = new ServiceProxy(clientId);
    }

    public double deposit(String account, double amount) throws IOException {
        byte[] reply = proxy.invokeOrdered(buildRequest(BankServer.DEPOSIT, account, amount));
        return parseReply(reply);
    }

    public double withdraw(String account, double amount) throws IOException {
        byte[] reply = proxy.invokeOrdered(buildRequest(BankServer.WITHDRAW, account, amount));
        return parseReply(reply);
    }

    public double balance(String account) throws IOException {
        byte[] reply = proxy.invokeUnordered(buildRequest(BankServer.BALANCE, account, 0));
        return parseReply(reply);
    }

    private byte[] buildRequest(int op, String account, double amount) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(op);
        out.writeUTF(account);
        if (op != BankServer.BALANCE) out.writeDouble(amount);
        return bos.toByteArray();
    }

    private double parseReply(byte[] reply) throws IOException {
        if (reply == null || reply.length == 0) throw new IOException("Sem resposta das réplicas");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(reply));
        boolean ok = in.readBoolean();
        double val = in.readDouble();
        if (!ok) System.err.println("[AVISO] Operação rejeitada (saldo insuficiente?)");
        return val;
    }

    public void close() {
        proxy.close();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Uso: java BankClient <clientId> <conta> <deposit|withdraw|balance> [valor]");
            System.exit(-1);
        }

        BankClient client = new BankClient(Integer.parseInt(args[0]));
        String account = args[1];
        String op = args[2];
        double amount = args.length > 3 ? Double.parseDouble(args[3]) : 0;

        double result;
        switch (op) {
            case "deposit":
                result = client.deposit(account, amount);
                break;
            case "withdraw":
                result = client.withdraw(account, amount);
                break;
            case "balance":
                result = client.balance(account);
                break;
            default:
                System.err.println("Operação desconhecida: " + op);
                result = -1;
        }

        System.out.printf("Resultado: %.2f%n", result);
        client.close();
    }
}