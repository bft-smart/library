package bftsmart.tests.normal;

import worker.IProcessingResult;

import java.util.Arrays;

public class Measurement implements IProcessingResult {
	private final String[][] measurements;

	public Measurement(String[]... measurements) {
		this.measurements = measurements;
	}

	public String[][] getMeasurements() {
		return measurements;
	}

	@Override
	public String toString() {
		return "Measurement{" +
				"measurements=" + Arrays.deepToString(measurements) +
				'}';
	}
}
