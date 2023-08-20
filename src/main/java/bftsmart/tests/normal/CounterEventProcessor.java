package bftsmart.tests.normal;

import java.util.ArrayList;
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
	private final ArrayList<String> cpuMeasurements;
	private final ArrayList<String> memMeasurements;
	private final ArrayList<String> netMeasurements;

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
		String[] cpuTime = new String[cpuMeasurements.size()];
		String[] user = new String[cpuMeasurements.size()];
		String[] sys = new String[cpuMeasurements.size()];

		int i = 0;
		for (String measurement : cpuMeasurements) {
			String[] strValues = measurement.split("\\s+");
			cpuTime[i] = strValues[0];
			user[i] = strValues[2];
			sys[i] = strValues[4];
			i++;
		}


		String[] netTime = new String[netMeasurements.size()];
		String[] iface = new String[netMeasurements.size()];
		String[] r = new String[netMeasurements.size()];
		String[] t = new String[netMeasurements.size()];

		i = 0;
		for (String measurement : netMeasurements) {
			String[] strValues = measurement.split("\\s+");
			netTime[i] = strValues[0];
			iface[i] = strValues[1];
			r[i] = strValues[4];
			t[i] = strValues[5];
			i++;
		}


		String[] memTime = new String[memMeasurements.size()];
		String[] memUsed = new String[memMeasurements.size()];

		i = 0;
		for (String measurement : memMeasurements) {
			String[] strValues = measurement.split("\\s+");
			memTime[i] = strValues[0];
			memUsed[i] = strValues[4];
			i++;
		}


		return new Measurement(cpuTime, user, sys, memTime, memUsed, netTime, iface, r, t);
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