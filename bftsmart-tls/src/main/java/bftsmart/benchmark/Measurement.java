package bftsmart.benchmark;

import worker.IProcessingResult;

import java.util.Arrays;

public class Measurement implements IProcessingResult {
	private final long[][] measurements;

	public Measurement(long[]... measurements) {
		this.measurements = measurements;
	}

	public long[][] getMeasurements() {
		return measurements;
	}

	@Override
	public String toString() {
		return "Measurement{" +
				"measurements=" + Arrays.toString(measurements) +
				'}';
	}
}
