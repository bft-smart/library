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
package bftsmart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * 
 * @author Marcel Santos
 *
 */
public class ConsoleLogger extends Thread {

	private InputStream in = null;
	private PrintStream out = null;
	private String index;

	public InputStream getIn() {
		return in;
	}
	public void setIn(InputStream in) {
		this.in = in;
	}
	public PrintStream getOut() {
		return out;
	}
	public void setOut(PrintStream out) {
		this.out = out;
	}
	public String getIndex() {
		return index;
	}
	public void setIndex(String index) {
		this.index = index;
	}
	
	public void run() {
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(in));
		String s;
		try {
			while ((s = stdInput.readLine()) != null) {
				out.println("Replica " + index + ")" +s);
			}
		} catch(IOException ioe) {
			System.out.println("----------- Exception writing replica log: " + ioe.getMessage());
		}
	}
}
