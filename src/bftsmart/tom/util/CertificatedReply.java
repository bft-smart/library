/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import threshsig.SigShare;

/**
 *
 * @author joao
 */
public class CertificatedReply implements Externalizable {
    
    private byte[] reply;
    
    private int clientID;
    
    private int session;
    
    private int sequence;
    
    private SigShare[] shares;

    public CertificatedReply(byte[] reply, int clientID, int session, int sequence, SigShare[] shares) {
        this.reply = reply;
        this.clientID = clientID;
        this.session = session;
        this.sequence = sequence;
        this.shares = shares;
    }

    public CertificatedReply() {}
    
    public byte[] getReply() {
        return reply;
    }

    public int getClientID() {
        return clientID;
    }

    public int getSession() {
        return session;
    }

    public int getSequence() {
        return sequence;
    }

    public SigShare[] getShares() {
        return shares;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        
        out.writeInt(clientID);
        out.writeInt(session);
        out.writeInt(sequence);

        out.writeInt(reply.length);
        out.write(reply);
        
        out.writeInt(shares.length);
        for (int i = 0; i < shares.length; i++) out.writeObject(shares[i]);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        
        clientID = in.readInt();
        session = in.readInt();
        sequence = in.readInt();
         
        reply = new byte[in.readInt()];
        in.readFully(reply);
        
        shares = new SigShare[in.readInt()];
        for (int i = 0; i < shares.length; i++) shares[i] = (SigShare) in.readObject();
        
    }
    
}
