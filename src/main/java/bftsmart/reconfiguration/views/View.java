/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.reconfiguration.views;

import bftsmart.aware.decisions.WeightConfiguration;

import java.io.Serializable;
import java.util.Arrays;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author eduardo
 */
public class View implements Serializable {

    private static final long serialVersionUID = 4052550874674512359L;

    private int id;
    private int f;
    private int[] processes;
    private Map<Integer, InetSocketAddress> addresses;
    private boolean isBFT;
    private int delta;
    private int u;

    //quorum overlay and weights
    private int overlayF;
    private int overlayN;
    private Map<Integer, Double> weights;

    public View(int id, int[] processes, int f, InetSocketAddress[] addresses, boolean isBFT, int delta) {
        this.id = id;
        this.processes = processes;
        this.isBFT = isBFT;
        this.addresses = new HashMap<Integer, InetSocketAddress>();
        this.weights = new HashMap<Integer, Double>();
        this.f = f;
        this.delta = delta;

        for (int i = 0; i < this.processes.length; i++)
            this.addresses.put(processes[i], addresses[i]);
        if (delta > 0) {
            computeWeights();
        } else {
            overlayF = this.f;
            overlayN = this.processes.length;
            u = 0;
            for (int i = 0; i < this.processes.length; i++)
                this.weights.put(processes[i], 1.00);
        }

        Arrays.sort(this.processes);
    }

    public View(int id, int[] processes, int f, InetSocketAddress[] addresses, boolean isBFT, int delta,
                WeightConfiguration weightConfiguration) {

        this(id, processes, f, addresses, isBFT, delta);
        this.setWeights(weightConfiguration);
    }

    public InetSocketAddress[] getAddresses() {
        InetSocketAddress[]  addrs = new InetSocketAddress[processes.length];
        for (int i = 0; i < processes.length; i++) {
            addrs[i] = addresses.get(i);
        }
        return addrs;
    }

    public boolean isBFT() {
        return isBFT;
    }

    public int getDelta() {
        return delta;
    }

    private void computeWeights() {

        overlayF = delta + f;

        if (isBFT) {
            u = 2 * f;
            overlayN = (3 * overlayF) + 1;
        } else {
            u = f;
            overlayN = (2 * overlayF) + 1;
        }

        double wMax = 1.00 + ((double) delta / (double) f);
        double wMin = 1.00;

        for (int i = 0; i < this.processes.length; i++)
            this.weights.put(processes[i], wMin);

        for (int i = 0; i < u; i++)
            this.weights.put(processes[i], wMax);

    }

    public boolean isMember(int id) {
        for (int i = 0; i < this.processes.length; i++) {
            if (this.processes[i] == id) {
                return true;
            }
        }
        return false;
    }


    public int getPos(int id) {
        for (int i = 0; i < this.processes.length; i++) {
            if (this.processes[i] == id) {
                return i;
            }
        }
        return -1;
    }

    public int getId() {
        return id;
    }

    public int getF() {
        return f;
    }

    public int getN() {
        return this.processes.length;
    }

    public int[] getProcesses() {
        return processes;
    }

    private void setWeights(WeightConfiguration w) {
        double wMax = 1.00 + ((double) delta / (double) f);
        double wMin = 1.00;
        this.weights = new HashMap<Integer, Double>();
        for (int replica: w.getR_max())
            this.weights.put(replica, wMax);

        for (int replica: w.getR_min())
            this.weights.put(replica,wMin);
    }

    public synchronized double getWeight(int p) {

        return weights.get(p);
    }

    public Map<Integer, Double> getWeights() {
        return weights;
    }

    public int getOverlayF() {
        return overlayF;
    }

    public int getOverlayN() {
        return overlayN;
    }

    public String getViewString() {
        String s = "";
        for (int i = 0; i < this.getN(); i++) {
          if (weights.get(i) > 1.00)
              s += i + "_";
        }
        return s;
    }

    @Override
    public String toString() {
        String ret = "ID:" + id + "; F:" + f + "; N_O:" + overlayN + "; F_O:" + overlayF + "; D: " + delta + "; Processes:";
        for (int i = 0; i < processes.length; i++) {
            ret = ret + processes[i] + "(" + addresses.get(processes[i]) + ",W: " + weights.get(processes[i]) + "),";
        }

        return ret;
    }

    public InetSocketAddress getAddress(int id) {
        return addresses.get(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof View) {
            View v = (View) obj;
            return (this.addresses.equals(v.addresses) &&
//            return (this.addresses.keySet().equals(v.addresses.keySet()) &&
                    this.weights.equals(v.weights) &&
                    Arrays.equals(this.processes, v.processes) &&
                    this.id == v.id && this.f == v.f && this.u == v.u &&
                    this.isBFT == v.isBFT && this.delta == v.delta &&
                    this.overlayF == v.overlayF && this.overlayN == v.overlayN);

        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + this.id;
        hash = hash * 31 + this.f;
        hash = hash * 31 + this.u;
        hash = hash * 31 + this.overlayF;
        hash = hash * 31 + this.overlayN;
        hash = hash * 31 + this.delta;
        hash = hash * 31 + (this.isBFT ? 1 : 0);
        if (this.processes != null) {
            for (int i = 0; i < this.processes.length; i++) hash = hash * 31 + this.processes[i];
        } else {
            hash = hash * 31 + 0;
        }
        hash = hash * 31 + this.addresses.hashCode();
        hash = hash * 31 + this.weights.hashCode();
        return hash;
    }

    /**
     * This method evaluates if the current configuration is the fastest possible
     * @return true if current config is the fastest, false otherwise
     */
    public boolean isFastestConfig(){
        return this.f ==  (int) Math.round((processes.length-1)/6.0); // it is faster configuration if t == T/2
    }

    public boolean isSaferConfig(){
        return this.f == (processes.length - 1)/3;
    }
}
