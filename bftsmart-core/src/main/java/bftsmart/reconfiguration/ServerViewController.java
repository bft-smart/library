/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.reconfiguration;

import java.net.InetSocketAddress;
import java.util.*;

import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.TOMUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author eduardo
 */
public class ServerViewController extends ViewController {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final int ADD_SERVER = 0;
    public static final int REMOVE_SERVER = 1;
    public static final int CHANGE_F = 2;
    // Role-aware reconfiguration (listeners / learners):
    public static final int ADD_LISTENER = 3;        // add a new non-voting member (id:ip:port:portRR)
    public static final int PROMOTE_TO_VOTER = 4;    // listener -> voter (id)
    public static final int DEMOTE_TO_LISTENER = 5;  // voter -> listener (id)
    
    private int quorumBFT; // ((n + f) / 2) replicas
    private int quorumCFT; // (n / 2) replicas
    private int[] otherProcesses;
    private int[] lastJoinStet;
    private List<TOMMessage> updates = new LinkedList<TOMMessage>();
    private TOMLayer tomLayer;
   // protected View initialView;
    
    public ServerViewController(int procId, KeyLoader loader) {
        this(procId,"", loader);
        /*super(procId);
        initialView = new View(0, getStaticConf().getInitialView(), 
                getStaticConf().getF(), getInitAdddresses());
        getViewStore().storeView(initialView);
        reconfigureTo(initialView);*/
    }

    public ServerViewController(int procId, String configHome, KeyLoader loader) {
        super(procId, configHome, loader);
        View cv = getViewStore().readView();
        if(cv == null){
            
            logger.info("Creating current view from configuration file");
            reconfigureTo(new View(0, getStaticConf().getInitialView(),
                getStaticConf().getF(), getInitAdddresses(),
                getStaticConf().getListeners(), getListenerInitAddresses()));
        }else{
            logger.info("Using view stored on disk");
            reconfigureTo(cv);
        }
       
    }

    private InetSocketAddress[] getInitAdddresses() {

        int nextV[] = getStaticConf().getInitialView();
        InetSocketAddress[] addresses = new InetSocketAddress[nextV.length];
        for (int i = 0; i < nextV.length; i++) {
            addresses[i] = getStaticConf().getRemoteAddress(nextV[i]);
        }

        return addresses;
    }

    private InetSocketAddress[] getListenerInitAddresses() {
        int[] ls = getStaticConf().getListeners();
        InetSocketAddress[] addresses = new InetSocketAddress[ls.length];
        for (int i = 0; i < ls.length; i++) {
            addresses[i] = getStaticConf().getRemoteAddress(ls[i]);
        }
        return addresses;
    }
    
    public void setTomLayer(TOMLayer tomLayer) {
        this.tomLayer = tomLayer;
    }

    
    public boolean isInCurrentView() {
        return this.currentView.isMember(getStaticConf().getProcessId());
    }

    /**
     * @return whether this process belongs to the current view in any role,
     *         i.e. as a voter ({@link #isInCurrentView()}) or as a listener.
     */
    public boolean isInCurrentViewAnyRole() {
        return this.currentView.isInView(getStaticConf().getProcessId());
    }

    /**
     * @return whether this process is a non-voting member (listener) of the current view.
     */
    public boolean amIListener() {
        return this.currentView.isListener(getStaticConf().getProcessId());
    }

    public int[] getCurrentViewOtherAcceptors() {
        return this.otherProcesses;
    }
    public int[] getReplicasWithout(int[] replicas, int without) {
        int index = -1;
        for (int i = 0; i < replicas.length; i++) {
            if (replicas[i] == without) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            System.arraycopy(replicas, index + 1, replicas, index, replicas.length - index - 1);
            replicas = Arrays.copyOfRange(replicas, 0, replicas.length - 1);
        }
        return replicas;
    }

    public int[] getCurrentViewAcceptors() {
        return this.currentView.getProcesses();
    }

    public boolean hasUpdates() {
        return !this.updates.isEmpty();
    }

    public void enqueueUpdate(TOMMessage up) {
        ReconfigureRequest request = (ReconfigureRequest) TOMUtil.getObject(up.getContent());
        if (request != null && request.getSender() == getStaticConf().getTTPId() 
                && TOMUtil.verifySignature(getStaticConf().getPublicKey(request.getSender()),
                    request.toString().getBytes(), request.getSignature())) {
            //if (request.getSender() == getStaticConf().getTTPId()) {
                this.updates.add(up);
        } else {
            logger.warn("Invalid reconfiguration from {}, discarding", up.getSender());
        }
    }

    public byte[] executeUpdates(int cid) {


        List<Integer> jSet = new LinkedList<>();
        List<Integer> rSet = new LinkedList<>();
        int f = -1;

        List<String> jSetInfo = new LinkedList<>();

        // Role-aware reconfiguration sets (listeners / learners):
        List<Integer> listenerJSet = new LinkedList<>();   // new listeners to add
        List<String> listenerJSetInfo = new LinkedList<>();
        List<Integer> promoteSet = new LinkedList<>();     // listener -> voter
        List<Integer> demoteSet = new LinkedList<>();      // voter -> listener


        for (int i = 0; i < updates.size(); i++) {
            ReconfigureRequest request = (ReconfigureRequest) TOMUtil.getObject(updates.get(i).getContent());
            Iterator<Integer> it = request.getProperties().keySet().iterator();

            while (it.hasNext()) {
                int key = it.next();
                String value = request.getProperties().get(key);

                if (key == ADD_SERVER) {
                    StringTokenizer str = new StringTokenizer(value, ":");
                    if (str.countTokens() > 2) {
                        int id = Integer.parseInt(str.nextToken());
                        if(!isCurrentViewMember(id) && !contains(id, jSet)){
                            jSetInfo.add(value);
                            jSet.add(id);
                            String host = str.nextToken();
                            int port = Integer.valueOf(str.nextToken());
                            int portRR = Integer.valueOf(str.nextToken());
                            this.getStaticConf().addHostInfo(id, host, port, portRR);                        }
                    }
                } else if (key == REMOVE_SERVER) {
                    if (isCurrentViewMemberOrListener(Integer.parseInt(value))) {
                        rSet.add(Integer.parseInt(value));
                    }
                } else if (key == CHANGE_F) {
                    f = Integer.parseInt(value);
                } else if (key == ADD_LISTENER) {
                    StringTokenizer str = new StringTokenizer(value, ":");
                    if (str.countTokens() > 2) {
                        int id = Integer.parseInt(str.nextToken());
                        if (!isCurrentViewMemberOrListener(id) && !contains(id, listenerJSet)) {
                            listenerJSetInfo.add(value);
                            listenerJSet.add(id);
                            String host = str.nextToken();
                            int port = Integer.valueOf(str.nextToken());
                            int portRR = Integer.valueOf(str.nextToken());
                            this.getStaticConf().addHostInfo(id, host, port, portRR);
                        }
                    }
                } else if (key == PROMOTE_TO_VOTER) {
                    int id = Integer.parseInt(value);
                    if (isCurrentViewListener(id) && !contains(id, promoteSet)) {
                        promoteSet.add(id);
                    }
                } else if (key == DEMOTE_TO_LISTENER) {
                    int id = Integer.parseInt(value);
                    if (isCurrentViewMember(id) && !contains(id, demoteSet)) {
                        demoteSet.add(id);
                    }
                }
            }

        }
        return reconfigure(jSetInfo, jSet, rSet, f, cid, listenerJSetInfo, listenerJSet, promoteSet, demoteSet);
    }

    private boolean contains(int id, List<Integer> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == id) {
                return true;
            }
        }
        return false;
    }

    private byte[] reconfigure(List<String> jSetInfo, List<Integer> jSet, List<Integer> rSet, int f, int cid) {
        return reconfigure(jSetInfo, jSet, rSet, f, cid,
                new LinkedList<String>(), new LinkedList<Integer>(),
                new LinkedList<Integer>(), new LinkedList<Integer>());
    }

    private byte[] reconfigure(List<String> jSetInfo, List<Integer> jSet, List<Integer> rSet, int f, int cid,
                               List<String> listenerJSetInfo, List<Integer> listenerJSet,
                               List<Integer> promoteSet, List<Integer> demoteSet) {

        boolean forceLC = false;

        // ----- Compute the new voter set -----
        // voters = (current voters + new voters + promoted listeners) - removed - demoted
        LinkedHashSet<Integer> voters = new LinkedHashSet<>();
        for (int v : jSet) voters.add(v);                 // new voters first (kept in lastJoinSet)
        for (int v : promoteSet) voters.add(v);           // promoted listeners become voters
        for (int v : currentView.getProcesses()) {
            if (!contains(v, rSet) && !contains(v, demoteSet)) {
                voters.add(v);
            } else if (tomLayer.execManager.getCurrentLeader() == v) {
                forceLC = true; // the current leader is leaving the voter set
            }
        }

        // ----- Compute the new listener set -----
        // listeners = (current listeners + new listeners + demoted voters) - removed - promoted
        LinkedHashSet<Integer> listeners = new LinkedHashSet<>();
        for (int l : listenerJSet) listeners.add(l);
        for (int l : demoteSet) listeners.add(l);
        for (int l : currentView.getListeners()) {
            if (!contains(l, rSet) && !contains(l, promoteSet)) {
                listeners.add(l);
            }
        }
        // A node cannot be both a voter and a listener: voters win.
        listeners.removeAll(voters);

        // Newly joining nodes (voters + listeners) for the connection/join heuristics.
        lastJoinStet = new int[jSet.size()];
        for (int i = 0; i < jSet.size(); i++) lastJoinStet[i] = jSet.get(i);

        int[] nextV = toIntArray(voters);
        int[] nextL = toIntArray(listeners);

        if (f < 0) {
            f = currentView.getF();
        }

        InetSocketAddress[] addresses = new InetSocketAddress[nextV.length];
        for (int i = 0; i < nextV.length; i++)
            addresses[i] = getStaticConf().getRemoteAddress(nextV[i]);

        InetSocketAddress[] listenerAddresses = new InetSocketAddress[nextL.length];
        for (int i = 0; i < nextL.length; i++)
            listenerAddresses[i] = getStaticConf().getRemoteAddress(nextL[i]);

        View newV = new View(currentView.getId() + 1, nextV, f, addresses, nextL, listenerAddresses);

        // The reply must advertise every node that needs to install the new view: new voters,
        // new listeners and promoted/demoted nodes.
        List<String> joinInfo = new LinkedList<>(jSetInfo);
        joinInfo.addAll(listenerJSetInfo);

        logger.info("New view: " + newV);
        logger.info("Installed on CID: " + cid);
        logger.info("lastJoinSet: " + jSet);

        //TODO:Remove all information stored about each process in rSet
        //processes execute the leave!!!
        reconfigureTo(newV);

        // Load the public keys of every member (voters and listeners) so that messages
        // and decision proofs from/to them can be authenticated.
        for (int process : newV.getAllMembers()) {
            tomLayer.loadPublicKey(process);
        }

        if (forceLC) {

            //TODO: Reactive it and make it work
            logger.info("Shortening LC timeout");
            tomLayer.requestsTimer.stopTimer();
            tomLayer.requestsTimer.setShortTimeout(3000);
            tomLayer.requestsTimer.startTimer();
            //tomLayer.triggerTimeout(new LinkedList<TOMMessage>());

        }
        return TOMUtil.getBytes(new ReconfigureReply(newV, joinInfo.toArray(new String[0]),
                 cid, tomLayer.execManager.getCurrentLeader()));
    }

    private static int[] toIntArray(Collection<Integer> c) {
        int[] a = new int[c.size()];
        int i = 0;
        for (int v : c) a[i++] = v;
        return a;
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
             int portRR = Integer.valueOf(str.nextToken());
             this.getStaticConf().addHostInfo(id, host, port, portRR);
        }
    }

    
    @Override
    public final void reconfigureTo(View newView) {
        this.currentView = newView;
        getViewStore().storeView(this.currentView);
        if (newView.isInView(getStaticConf().getProcessId())) {
            // Part of the current view as a voter or as a listener.
            // 'otherProcesses' are the other acceptors (voters) to talk to. A voter excludes
            // itself; a listener is not a voter, so all voters are "other" acceptors.
            int myId = getStaticConf().getProcessId();
            int[] voters = currentView.getProcesses();
            boolean meIsVoter = currentView.isMember(myId);
            otherProcesses = new int[meIsVoter ? voters.length - 1 : voters.length];
            int c = 0;
            for (int voter : voters) {
                if (voter != myId) {
                    otherProcesses[c++] = voter;
                }
            }

            this.quorumBFT = (int) Math.ceil((this.currentView.getN() + this.currentView.getF()) / 2);
            this.quorumCFT = (int) Math.ceil(this.currentView.getN() / 2);
        } else if (this.currentView != null && this.currentView.isMember(getStaticConf().getProcessId())) {
            //TODO: Left the system in newView -> LEAVE
            //CODE for LEAVE   
        }else{
            //TODO: Didn't enter the system yet
            
        }
    }

    /*public int getQuorum2F() {
        return quorum2F;
    }*/
    

    public int getQuorum() {
        return getStaticConf().isBFT() ? quorumBFT : quorumCFT;
    }

    public TOMLayer getTOMLayer() {
        return tomLayer;
    }
}
