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
package bftsmart.tom.core.messages;

/**
 * Possible types of TOMMessage
 * 
 * @author alysson
 */
public enum TOMMessageType {
    ORDERED_REQUEST, //0
    UNORDERED_REQUEST, //1
    REPLY, //2
    RECONFIG, //3
    ASK_STATUS, // 4
    STATUS_REPLY,// 5
    UNORDERED_HASHED_REQUEST, //6
	ORDERED_HASHED_REQUEST; //7

	public static TOMMessageType[] values = values();

	public static TOMMessageType getMessageType(int ordinal) {
		return values[ordinal];
	}
}
