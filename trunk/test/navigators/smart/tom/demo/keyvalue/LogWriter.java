package navigators.smart.tom.demo.keyvalue;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class LogWriter extends Thread {

	private InputStream in = null;
	private int index;

	public InputStream getIn() {
		return in;
	}
	public void setIn(InputStream in) {
		this.in = in;
	}
	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	
	public void run() {
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(in));
		String s;
		try {
			PrintWriter pw = new PrintWriter(new FileWriter("ServerLog-" + index + ".debug"));
			while ((s = stdInput.readLine()) != null) {
				pw.println(s);
			}
			pw.close();
		} catch(IOException ioe) {
			System.out.println("----------- Exception writing replica log: " + ioe.getMessage());
		}
	}
}
