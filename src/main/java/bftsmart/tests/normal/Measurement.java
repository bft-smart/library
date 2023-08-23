package bftsmart.tests.normal;

import worker.IProcessingResult;

import java.util.Arrays;

public class Measurement implements IProcessingResult {
	private final double[][] measurements;
	private final String[] header;

	public Measurement(double[]... measurements) {
		this.measurements = measurements;
		this.header = null;
	}

	public Measurement(String[] header, double[]... measurements) {
		this.measurements = measurements;
		this.header = header;
	}

	public double[][] getMeasurements() {
		return measurements;
	}

	public String[] getHeader(){
		return header;
	}

	@Override
	public String toString() {
		return "Measurement{" +
				"measurements=" + Arrays.toString(measurements) +
				'}';
	}
}
