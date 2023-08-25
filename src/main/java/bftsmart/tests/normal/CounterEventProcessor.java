package bftsmart.tests.normal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

public class CounterEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static String SAR_READY_PATTERN;
	private boolean isReady;
	private ArrayList<String> cpuMeasurements;
	private ArrayList<String> memMeasurements;
	private ArrayList<String> netMeasurements;
	private String pattern;
	private Pattern timePattern;

	public CounterEventProcessor() {
		SAR_READY_PATTERN = "%";
		cpuMeasurements=new ArrayList<>();
		memMeasurements=new ArrayList<>();
		netMeasurements=new ArrayList<>();
		pattern = "^\\d{2}:\\d{2}:\\d{2}";
		timePattern = Pattern.compile(pattern);
	}

	@Override
	public void process(String line) {
		logger.debug("{}", line);
		if (!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN) || line.contains(SAR_READY_PATTERN))) 
			isReady = true;

		Matcher matcher = timePattern.matcher(line);

		if (matcher.find() && !line.contains("%")) {
			int ncol = line.split("\\s+").length;
			if(ncol==8){
				cpuMeasurements.add(line);
			}else if(ncol==10){
				netMeasurements.add(line);
			}else{
				memMeasurements.add(line);
			}
		}
	
	}

	@Override
	public void startProcessing() {
	}

	@Override
	public void stopProcessing() {
	}

	@Override
	public IProcessingResult getProcessingResult() {

		double[] user = new double[cpuMeasurements.size()];
		double[] sys = new double[cpuMeasurements.size()];
		int i = 0;
		for (String measurement : cpuMeasurements) {
			String[] strValues = Arrays.stream(measurement.split("\\s+")).map(val -> val.replace(",",".")).toArray(String[]::new);
			double[] dValues = Arrays.stream(Arrays.copyOfRange(strValues, 2, strValues.length)).mapToDouble(Double::parseDouble).toArray();
			user[i] = dValues[0];
			sys[i] = dValues[2];
			i++;
		}


		String[] iface = new String[netMeasurements.size()];
		double[] r = new double[netMeasurements.size()];
		double[] t = new double[netMeasurements.size()];
		i = 0;
		for (String measurement : netMeasurements) {
			String[] strValues = Arrays.stream(measurement.split("\\s+")).map(val -> val.replace(",",".")).toArray(String[]::new);
			double[] dValues = Arrays.stream(Arrays.copyOfRange(strValues, 2, strValues.length)).mapToDouble(Double::parseDouble).toArray();
			iface[i] = strValues[1];
			r[i] = dValues[2];
			t[i] = dValues[3];
			i++;
		}
		iface = Arrays.stream(iface).distinct().toArray(String[]::new);


		double[] memUsed = new double[memMeasurements.size()];
		i = 0;
		for (String measurement : memMeasurements) {
			String[] strValues = Arrays.stream(measurement.split("\\s+")).map(val -> val.replace(",",".")).toArray(String[]::new);
			double[] dValues = Arrays.stream(Arrays.copyOfRange(strValues, 1, strValues.length)).mapToDouble(Double::parseDouble).toArray();
			memUsed[i] = dValues[3];
			i++;
		}


		return new Measurement(iface, user, sys, memUsed, r, t);
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public boolean ended() {
		return false;
	}

}