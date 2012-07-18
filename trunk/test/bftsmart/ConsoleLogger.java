package bftsmart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

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
