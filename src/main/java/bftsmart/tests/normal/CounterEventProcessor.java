package bftsmart.tests.normal;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

public class CounterEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String CLIENT_END_PATTERN = "Experiment executed";
	ProcessBuilder startMonitor = new ProcessBuilder("bash", "-c", "nohup sar 1 -o \"./monitoring_data\" > /dev/null 2>&1 &");
	ProcessBuilder stopMonitor = new ProcessBuilder("bash", "-c", "kill $(pidof sar)");
	private boolean isReady;
	Process monitoring;

	@Override
	public void process(String line) {
		logger.debug("{}", line);
		if(!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN)))
		{
			isReady = true;
			//start monitor
			try {
				monitoring = startMonitor.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (isReady && line.contains(CLIENT_END_PATTERN)){
			//end monitor
			try {
				stopMonitor.start();
			} catch (IOException e) {
				e.printStackTrace();
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
		return null;
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
