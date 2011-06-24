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

package navigators.smart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import navigators.smart.reconfiguration.ReconfigurationManager;

/**
 * This class manages information about the leader of each round of each consensus
 * @author edualchieri
 */
public class LeaderModule {

    // Each value of this map is a list of all the rounds of a consensus
    // Each element of that list is a tuple which stands for a round, and the id
    // of the process that was the leader for that round
    private Map<Integer, List<ConsInfo>> leaderInfos = new HashMap<Integer, List<ConsInfo>>();
    
    // este e a nova maneira de guardar info sobre o lider, desacoplada do consenso
    private ReconfigurationManager reconfManager;
    private int currentTS;
    /**
     * Creates a new instance of LeaderModule
     */
    public LeaderModule(ReconfigurationManager reconfManager) {
        addLeaderInfo(-1, 0, 0);
        addLeaderInfo(0, 0, 0);
        this.reconfManager = reconfManager;
        currentTS = 0;
    }

    /**
     * Adds information about a leader
     * @param c Consensus where the replica is a leader
     * @param r Rounds of the consensus where the replica is a leader
     * @param l ID of the leader
     */
    public void addLeaderInfo(int c, int r, int l) {
        List list = leaderInfos.get(c);
        if (list == null) {
            list = new LinkedList();
            leaderInfos.put(c, list);
        }
        ConsInfo ci = findInfo(list, r);

        if (ci != null) {
            ci.leaderId = l;
        } else {
            list.add(new ConsInfo(r, l));
        }
    }
    
    /**
     * Define o novo timestamp que identifica o lider
     * @param ts novo timestamp que identifica o lider
     */
    public void setNewTS(int ts) {
        this.currentTS = ts;
    }
    
    /**
     * Obtem o lider currente, a partir do timestamp que tem
     * @return Lider currente
     */
    public int getCurrentLeader() {
        return (currentTS % this.reconfManager.getCurrentViewN());
    }
    /**
     * Retrieves the tuple for the specified round, given a list of tuples
     * @param l List of tuples formed by a round number and the ID of the leader
     * @param r Number of the round tobe searched
     * @return The tuple for the specified round, or null if there is none
     */
    private ConsInfo findInfo(List l, int r) {
        ConsInfo ret = null;
        for (int i = 0; i < l.size(); i++) {
            ret = (ConsInfo) l.get(i);
            if (ret.round == r) {
                return ret;
            }
        }
        return null;
    }

    /**
     * Invoked by the acceptor object when a value is decided
     * It adds a new tuple to the list, which corresponds to the next consensus
     *
     * @param c ID of the consensus established as being decided
     * @param l ID of the replica established as being the leader for the round 0 of the next consensus
     */
    public void decided(int c, int l) {
        if (leaderInfos.get(c) == null) {
            addLeaderInfo(c + 1, 0, l);
        }
    }

    /**
     * Retrieves the replica ID of the leader for the specified consensus's execution ID and round number
     * TODO: Isto e mais do que um getter. Sera q nao se devia mudar isto?
     * @param c consensus's execution ID
     * @param r Round number for the specified consensus
     * @return The replica ID of the leader
     */
    public int getLeader(int c, int r) {
        /***/
        List<ConsInfo> list = leaderInfos.get(c);
        if (list == null) {
            //there are no information for the execution c
            //let's see who were the leader of the next execution
            list = new LinkedList();
            leaderInfos.put(c, list);

            List<ConsInfo> before = leaderInfos.get(c - 1);

            if (before != null && before.size() > 0) {
                //the leader for this round will be the leader of
                ConsInfo ci = before.get(before.size() - 1);
                list.add(new ConsInfo(r, ci.leaderId));
                return ci.leaderId;
            }
        } else {
            for (int i = 0; i < list.size(); i++) {
                ConsInfo ci = list.get(i);
                if (ci.round == r) {
                    return ci.leaderId;
                }
            }
        }
        return -1;
        /***/
        //return 0;
    }

    /**
     * Removes a consensus that is established as being stable
     *
     * @param c Execution ID of the consensus
     */
     /******************* METODO ORIGINAL *************
    public void removeStableConsenusInfos(int c) {
        List list = leaderInfos.get(c + 1);

        if (list == null) {//nunca vai acontecer isso!!!
            System.err.println("- Executing a code that wasn't supposed to be executed :-)");
            System.err.println("- And we have some reports there is a bug here!");
            list = new LinkedList();
            leaderInfos.put(c + 1, list);
            List rm = leaderInfos.remove(c);
            ConsInfo ci = (ConsInfo) rm.get(rm.size() - 1);
            list.add(new ConsInfo(0, ci.leaderId));
        } else {
            leaderInfos.remove(c);
        }
    }
    /******************* METODO ORIGINAL *************/

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    private ReentrantLock leaderInfosLock = new ReentrantLock();

    public void removeStableConsenusInfos(int c) {

        leaderInfosLock.lock();

        //******* EDUARDO BEGIN: se é instável remove e pronto! **************//
        /*List list = leaderInfos.get(c + 1);

        if (list == null) {//nunca vai acontecer isso!!!
            System.err.println("- Executing a code that wasn't supposed to be executed :-)");
            System.err.println("- And we have some reports there is a bug here!");
            list = new LinkedList();
            leaderInfos.put(c + 1, list);
            List rm = leaderInfos.remove(c);
            if (rm != null) {
                ConsInfo ci = (ConsInfo) rm.get(rm.size() - 1);
                list.add(new ConsInfo(0, ci.leaderId));
            }
        } else {*/
            leaderInfos.remove(c);
        //}

        leaderInfosLock.unlock();

        //******* EDUARDO BEGIN **************//
    }

    public void removeStableMultipleConsenusInfos(int cStart, int cEnd) {

        leaderInfosLock.lock();

        List list = leaderInfos.get(cEnd + 1);

        if (list == null) {//nunca vai acontecer isso!!!
            //System.err.println("- Executing a code that wasn't supposed to be executed :-)");
            //System.err.println("- And we have some reports there is a bug here!");
            list = new LinkedList();
            leaderInfos.put(cEnd + 1, list);
            List rm = leaderInfos.get(cEnd);
            if (rm != null) {
                    ConsInfo ci = (ConsInfo) rm.get(rm.size() - 1);
                    list.add(new ConsInfo(0, ci.leaderId));
            }
        }

        for (int c = cStart; c <= cEnd; c++) {

                leaderInfos.remove(c);

        }

        leaderInfosLock.unlock();
    }
    /********************************************************/

    /**
     * This class represents a tuple formed by a round number and the replica ID of that round's leader
     */
    private class ConsInfo {

        public int round;
        public int leaderId;

        public ConsInfo(int r, int l) {
            this.round = r;
            this.leaderId = l;
        }
    }
}
