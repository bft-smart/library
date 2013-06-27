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
package bftsmart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages information about the leader of each round of each consensus
 * @author edualchieri
 */
public class LeaderModule {

	// Each value of this map is a list of all the rounds of a consensus
	// Each element of that list is a tuple which stands for a round, and the id
	// of the process that was the leader for that round
	private Map<Integer, List<ConsInfo>> leaderInfos = new HashMap<Integer, List<ConsInfo>>();

	// This is the new way of storing info about the leader, uncoupled from consensus
	private int currentLeader;
	/**
	 * Creates a new instance of LeaderModule
	 */
	public LeaderModule() {
		addLeaderInfo(-1, 0, 0);
		addLeaderInfo(0, 0, 0);
		currentLeader = 0;
	}

	/**
	 * Adds information about a leader
	 * @param c Consensus where the replica is a leader
	 * @param r Rounds of the consensus where the replica is a leader
	 * @param l ID of the leader
	 */
	public void addLeaderInfo(int c, int r, int l) {
		List<ConsInfo> list = leaderInfos.get(c);
		if (list == null) {
			list = new LinkedList<ConsInfo>();
			leaderInfos.put(c, list);
		}
		ConsInfo ci = findInfo(list, r);

		if (ci != null) {
			ci.leaderId = l;
		} else {
			list.add(new ConsInfo(r, l));
		}
	}

	public void setNewLeader (int leader) {
		this.currentLeader = leader;
	}
	/**
	 * Get the current leader
	 * @return current leader
	 */
	public int getCurrentLeader() {
		return currentLeader;
	}

	/**
	 * Retrieves the tuple for the specified round, given a list of tuples
	 * @param l List of tuples formed by a round number and the ID of the leader
	 * @param r Number of the round to be searched
	 * @return The tuple for the specified round, or null if there is none
	 */
	private ConsInfo findInfo(List<ConsInfo> l, int r) {
		ConsInfo ret = null;
		for (int i = 0; i < l.size(); i++) {
			ret = l.get(i);
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
	 * TODO: This is more than a getter. Should'nt we change that?
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
			list = new LinkedList<ConsInfo>();
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

	/** THIS IS JOAO'S CODE, FOR HANDLING STATE TRANSFER */
	private ReentrantLock leaderInfosLock = new ReentrantLock();

	public void removeStableConsenusInfos(int c) {
		leaderInfosLock.lock();
		leaderInfos.remove(c);
		leaderInfosLock.unlock();
	}

	public void removeStableMultipleConsenusInfos(int cStart, int cEnd) {

		leaderInfosLock.lock();

		List<ConsInfo> list = leaderInfos.get(cEnd + 1);

		if (list == null) {//this will never happen!!!
			list = new LinkedList<ConsInfo>();
			leaderInfos.put(cEnd + 1, list);
			List<ConsInfo> rm = leaderInfos.get(cEnd);
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
