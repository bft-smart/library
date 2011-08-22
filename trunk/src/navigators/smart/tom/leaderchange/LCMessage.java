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

import navigators.smart.communication.SystemMessage;
import navigators.smart.tom.core.messages.*;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Mensagem usada na troca de lider e sincronizacao
 * @author Joao Sousa
 */
public class LCMessage extends SystemMessage {

    private int type;
    private int ts;
    private byte[] payload;

    /**
     * Constructor vazio
     */
    public LCMessage(){}


    /**
     * Constructor
     * @param from replica que fabrica este mensagem
     * @param type tipo de mensagem (STOP, SYNC, CATCH-UP)
     * @param ts timestamp da mudanca de lider e sincronizacao
     * @param payload dados que vem na mensagem
     */
    public LCMessage(int from, int type, int ts, byte[] payload) {
        super(from);
        this.type = type;
        this.ts = ts;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * Obter tipo de mensagem
     * @return tipo de mensagem
     */
    public int getType() {
        return type;
    }

    /**
     * Obter timestamp da mudanca de lider e sincronizacao
     * @return timestamp da mudanca de lider e sincronizacao
     */
    public int getTs() {
        return ts;
    }

    /**
     * Obter dados da mensagem
     * @return dados da mensagem
     */
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException{
        super.writeExternal(out);

        out.writeInt(type);
        out.writeInt(ts);
        out.writeObject(payload);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{
        super.readExternal(in);

        type = in.readInt();
        ts = in.readInt();
        payload = (byte[]) in.readObject();
    }
}
