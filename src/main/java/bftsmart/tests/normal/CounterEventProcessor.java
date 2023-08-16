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
	private final Logger Monitorlog = LoggerFactory.getLogger("monitoring");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String SAR_READY_PATTERN = "CPU     %user     %nice   %system   %iowait    %steal     %idle";
	private boolean isReady;
	ArrayList<String> measurements=new ArrayList<String>();

	@Override
	public void process(String line) {
		if (!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN) || line.contains(SAR_READY_PATTERN))) {
			isReady = true;
			logger.debug("{}", line);
		} else {
			String pattern = "^\\d{2}:\\d{2}:\\d{2}";
			Pattern timePattern = Pattern.compile(pattern);
			Matcher matcher = timePattern.matcher(line);

			if (matcher.find() || line.contains("Average:")) {
				Monitorlog.debug(line);
				measurements.add(line);
			}else{
				logger.debug("{}", line);
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
		String[] time = new String[measurements.size()];
		String[] cpu = new String[measurements.size()];
		String[] user = new String[measurements.size()];
		String[] nice = new String[measurements.size()];
		String[] sys = new String[measurements.size()];
		String[] iowait = new String[measurements.size()];
		String[] steal = new String[measurements.size()];
		String[] idle = new String[measurements.size()];
		int i = 0;
		for (String measurement : measurements) {
			String[] strValues = measurement.split("\\s+");
			time[i] = strValues[0];
			cpu[i] = strValues[1];
			user[i] = strValues[2];
			nice[i] = strValues[3];
			sys[i] = strValues[4];
			iowait[i] = strValues[5];
			steal[i] = strValues[6];
			idle[i] = strValues[7];
			i++;
		}
		return new Measurement(time, cpu, user, nice, sys, iowait, steal, idle);
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
