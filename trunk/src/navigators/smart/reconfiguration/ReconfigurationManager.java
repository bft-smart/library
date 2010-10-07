/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.reconfiguration;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import navigators.smart.paxosatwar.Consensus;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMUtil;

/**
 *
 * @author eduardo
 */
public class ReconfigurationManager extends ViewManager {

    public static final int ADD_SERVER = 0;
    public static final int REMOVE_SERVER = 1;
    public static final int CHANGE_F = 2;
    public static final int TOM_NORMAL_REQUEST = 0;
    public static final int TOM_RECONFIG_REQUEST = 1;
    private int quorumF; // f replicas
    private int quorum2F; // f * 2 replicas
    private int quorumStrong; // ((n + f) / 2) replicas
    private int quorumFastDecide; // ((n + 3 * f) / 2) replicas
    private int[] otherProcesses;
    private int[] lastJoinStet;
    private List<TOMMessage> updates = new LinkedList<TOMMessage>();
    private TOMLayer tomLayer;

    public ReconfigurationManager(int procId) {
        super(procId);
    }

    public ReconfigurationManager(int procId, String configHome) {
        super(procId, configHome);
    }

    public void setTomLayer(TOMLayer tomLayer) {
        this.tomLayer = tomLayer;
    }

    public View getInitialView() {
        return initialView;
    }

    public View getCurrentView() {
        return this.currentView;
    }

    public boolean isInInitView() {
        return this.initialView.isMember(getStaticConf().getProcessId());
    }

    public boolean isInCurrentView() {
        if (this.currentView != null) {
            return this.currentView.isMember(getStaticConf().getProcessId());
        }
        return false;
    }

    public int[] getCurrentViewOtherAcceptors() {
        return this.otherProcesses;
    }

    public int[] getCurrentViewAcceptors() {
        return this.currentView.getProcesses();
    }

    public boolean hasUpdates() {
        return !this.updates.isEmpty();
    }

    public void enqueueUpdate(TOMMessage up) {
        ReconfigureRequest request = (ReconfigureRequest) TOMUtil.getObject(up.getContent());
        if (TOMUtil.verifySignature(getStaticConf().getRSAPublicKey(request.getSender()),
                request.toString().getBytes(), request.getSignature())) {
            if (request.getSender() == getStaticConf().getTTPId()) {
                this.updates.add(up);
            } else {
                boolean add = true;
                Iterator<Integer> it = request.getProperties().keySet().iterator();
                while (it.hasNext()) {
                    int key = it.next();
                    String value = request.getProperties().get(key);
                    if (key == ADD_SERVER) {
                        StringTokenizer str = new StringTokenizer(value, ":");
                        if (str.countTokens() > 2) {
                            int id = Integer.parseInt(str.nextToken());
                            if(id != request.getSender()){
                                add = false;
                            }
                        }else{
                            add = false;
                        }
                    } else if (key == REMOVE_SERVER) {
                        if (isCurrentViewMember(Integer.parseInt(value))) {
                            if(Integer.parseInt(value) != request.getSender()){
                                add = false;
                            }
                        }else{
                            add = false;
                        }
                    } else if (key == CHANGE_F) {
                        add = false;
                    }
                }
                if(add){
                    this.updates.add(up);
                }
            }
        }
    }

    public byte[] executeUpdates(int eid, int decisionRound, byte[] state) {


        List jSet = new LinkedList();
        List rSet = new LinkedList();
        int f = -1;
        
        List jSetInfo = new LinkedList();
        
        
        for (int i = 0; i < updates.size(); i++) {
            ReconfigureRequest request = (ReconfigureRequest) TOMUtil.getObject(updates.get(i).getContent());
            Iterator<Integer> it = request.getProperties().keySet().iterator();

            while (it.hasNext()) {
                int key = it.next();
                String value = request.getProperties().get(key);

                if (key == ADD_SERVER) {
                    StringTokenizer str = new StringTokenizer(value, ":");
                    if (str.countTokens() > 2) {
                        jSetInfo.add(value);
                        int id = Integer.parseInt(str.nextToken());
                        jSet.add(id);
                        String host = str.nextToken();
                        int port = Integer.valueOf(str.nextToken());
                        this.getStaticConf().addHostInfo(id, host, port);
                    }
                } else if (key == REMOVE_SERVER) {
                    if (isCurrentViewMember(Integer.parseInt(value))) {
                        rSet.add(Integer.parseInt(value));
                    }
                } else if (key == CHANGE_F) {
                    f = Integer.parseInt(value);
                }
            }

        }




        //ret = reconfigure(updates.get(i).getContent());
        return reconfigure(jSetInfo, jSet, rSet, f, eid, decisionRound, state);
    }

    private boolean contains(int id, List<Integer> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == id) {
                return true;
            }
        }
        return false;
    }

    private byte[] reconfigure(List<String> jSetInfo, List<Integer> jSet, List<Integer> rSet, int f, int eid, int decisionRound, byte[] state) {
        //ReconfigureRequest request = (ReconfigureRequest) TOMUtil.getObject(req);
        // Hashtable<Integer, String> props = request.getProperties();
        // int f = Integer.valueOf(props.get(CHANGE_F));
        lastJoinStet = new int[jSet.size()];
        int[] nextV = new int[currentView.getN() + jSet.size() - rSet.size()];
        int p = 0;
        for (int i = 0; i < jSet.size(); i++) {
            lastJoinStet[i] = jSet.get(i);
            nextV[p++] = jSet.get(i);
        }

        for (int i = 0; i < currentView.getProcesses().length; i++) {
            if (!contains(currentView.getProcesses()[i], rSet)) {
                nextV[p++] = currentView.getProcesses()[i];
            }
        }

        if (f < 0) {
            f = currentView.getF();
        }

        View newV = new View(currentView.getId() + 1, nextV, f);

        System.out.println("new view: " + newV);
        System.out.println("lastJoinSet: " + jSet);

        reconfigureTo(newV);



        return TOMUtil.getBytes(new ReconfigureReply(newV, jSetInfo.toArray(new String[0]),
                tomLayer.getLastExec(), tomLayer.lm.getLeader(eid, decisionRound), state));
    }

    public TOMMessage[] clearUpdates() {
        TOMMessage[] ret = new TOMMessage[updates.size()];
        for (int i = 0; i < updates.size(); i++) {
            ret[i] = updates.get(i);
        }
        updates.clear();
        return ret;
    }

    public boolean isInLastJoinSet(int id) {
        if (lastJoinStet != null) {
            for (int i = 0; i < lastJoinStet.length; i++) {
                if (lastJoinStet[i] == id) {
                    return true;
                }
            }

        }
        return false;
    }

    public void processJoinResult(ReconfigureReply r) {
        this.reconfigureTo(r.getView());
        
        String[] s = r.getJoinSet();
        
        this.lastJoinStet = new int[s.length];
        
        for(int i = 0; i < s.length;i++){
             StringTokenizer str = new StringTokenizer(s[i], ":");
             int id = Integer.parseInt(str.nextToken());
             this.lastJoinStet[i] = id;
             String host = str.nextToken();
             int port = Integer.valueOf(str.nextToken());
             this.getStaticConf().addHostInfo(id, host, port);
        }
    }

    @Override
    public void reconfigureTo(View newView) {
        if (newView.isMember(getStaticConf().getProcessId())) {
            this.currentView = newView;
            otherProcesses = new int[currentView.getProcesses().length - 1];
            int c = 0;
            for (int i = 0; i < currentView.getProcesses().length; i++) {
                if (currentView.getProcesses()[i] != getStaticConf().getProcessId()) {
                    otherProcesses[c++] = currentView.getProcesses()[i];
                }
            }

            this.quorumF = this.currentView.getF();
            this.quorum2F = 2 * this.quorumF;
            this.quorumStrong = (int) Math.ceil((this.currentView.getN() + this.quorumF) / 2);
            this.quorumFastDecide = (int) Math.ceil((this.currentView.getN() + 3 * this.quorumF) / 2);
        } else if (this.currentView != null && this.currentView.isMember(getStaticConf().getProcessId())) {
            //TODO: Saiu do sistema em newView -> LEAVE
            this.currentView = newView;

        }
    }

    public int getQuorum2F() {
        return quorum2F;
    }

    public int getQuorumF() {
        return quorumF;
    }

    public int getQuorumFastDecide() {
        return quorumFastDecide;
    }

    public int getQuorumStrong() {
        return quorumStrong;
    }
}
