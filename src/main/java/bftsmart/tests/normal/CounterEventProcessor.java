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
	private static final String SAR_READY_PATTERN = "%";
	private boolean isReady;
	ArrayList<String> cpu_measurements=new ArrayList<String>();
	ArrayList<String> mem_measurements=new ArrayList<String>();
	ArrayList<String> net_measurements=new ArrayList<String>();

	@Override
	public void process(String line) {
		logger.debug("{}", line);
		if (!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN) || line.contains(SAR_READY_PATTERN))) 
			isReady = true;
		
		String pattern = "^\\d{2}:\\d{2}:\\d{2}";
		Pattern timePattern = Pattern.compile(pattern);
		Matcher matcher = timePattern.matcher(line);

		if ((matcher.find() || line.contains("Average:")) && !line.contains("%")) {
			int ncol=line.split("\\s+").length;
			if(ncol==8){
				cpu_measurements.add(line);
			}else if(ncol==10){
				net_measurements.add(line);
			}else{
				mem_measurements.add(line);
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
		String[] cpu_time = new String[cpu_measurements.size()];
		String[] user = new String[cpu_measurements.size()];
		String[] sys = new String[cpu_measurements.size()];

		int i = 0;
		for (String measurement : cpu_measurements) {
			String[] strValues = measurement.split("\\s+");
			cpu_time[i] = strValues[0];
			user[i] = strValues[2];
			sys[i] = strValues[4];
			i++;
		}


		String[] net_time = new String[net_measurements.size()];
		String[] iface = new String[net_measurements.size()];
		String[] r = new String[net_measurements.size()];
		String[] t = new String[net_measurements.size()];

		i = 0;
		for (String measurement : net_measurements) {
			String[] strValues = measurement.split("\\s+");
			net_time[i] = strValues[0];
			iface[i] = strValues[1];
			r[i] = strValues[4];
			t[i] = strValues[5];
			i++;
		}


		String[] mem_used = new String[mem_measurements.size()];

		i = 0;
		for (String measurement : mem_measurements) {
			String[] strValues = measurement.split("\\s+");
			mem_used[i] = strValues[4];
			i++;
		}


		return new Measurement(cpu_time, user, sys, mem_used, net_time, iface, r, t);
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