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
package bftsmart.tom.core;

/**
 * This class manages information about the leader of the SMR protocol
 * @author edualchieri
 */
public class LeaderModule {

	// This is the new way of storing info about the leader,
        // uncoupled from any consensus instance
	private int currentLeader;
        
	/**
	 * Creates a new instance of LeaderModule
	 */
	public LeaderModule() {

		currentLeader = 0;
	}

        /**
         * Set the current leader
         * @param leader Current leader
         */
	public void setNewLeader (int leader) {
		this.currentLeader = leader;
	}
        
	/**
	 * Get the current leader
	 * @return Current leader
	 */
	public int getCurrentLeader() {
		return currentLeader;
	}
}
