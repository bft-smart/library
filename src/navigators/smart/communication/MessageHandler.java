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

package navigators.smart.communication;


import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.ForwardedMessage;
import navigators.smart.tom.util.TOMUtil;
import navigators.smart.tom.leaderchange.LCMessage;


/**
 *
 * @author edualchieri
 */
public class MessageHandler {

    private Acceptor acceptor;
    private TOMLayer tomLayer;

    public void setAcceptor(Acceptor acceptor) {
        this.acceptor = acceptor;
    }

    public void setTOMLayer(TOMLayer tomLayer) {
        this.tomLayer = tomLayer;
    }

    protected void processData(SystemMessage sm) {
        if (sm instanceof PaxosMessage) {
            PaxosMessage paxosMsg = (PaxosMessage) sm;
            acceptor.deliver(paxosMsg);
        /*** ISTO E CODIGO DO JOAO, RELACIONADO COM A TROCA DE LIDER */
        } else if (sm instanceof LCMessage) {
            LCMessage lcMsg = (LCMessage) sm;
            
            String type = null;
            switch(lcMsg.getType()) {
                
                case TOMUtil.STOP:
                    type = "STOP";
                    break;
                case TOMUtil.STOPDATA:
                    type = "STOPDATA";
                    break;
                case TOMUtil.SYNC:
                    type = "SYNC";
                    break;
                default:
                    type = "LOCAL";
                    break;
            }
            
            System.out.println("(MessageHandler.processData) LC_MSG received: type " + type + ", regency " + lcMsg.getReg() + ", (replica " + lcMsg.getSender() + ")");
            if (lcMsg.TRIGGER_LC_LOCALLY) tomLayer.requestsTimer.run_lc_protocol();
            else tomLayer.deliverTimeoutRequest(lcMsg);
        /**************************************************************/

        } else if (sm instanceof ForwardedMessage) {
            TOMMessage request = ((ForwardedMessage) sm).getRequest();
            tomLayer.requestReceived(request);

        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
        } else if (sm instanceof SMMessage) {
            SMMessage smsg = (SMMessage) sm;

            if (smsg.TRIGGER_SM_LOCALLY) {
                tomLayer.getStateManager().stateTimeout();
            }
            else if (smsg.getType() == TOMUtil.SM_REQUEST) {
                tomLayer.getStateManager().SMRequestDeliver(smsg);
            } else {
                tomLayer.getStateManager().SMReplyDeliver(smsg);
            }
        /******************************************************************/
        }
    }
    
    protected void verifyPending() {
        tomLayer.processOutOfContext();
    }
}
