package bftsmart.tests.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

public class SimpleServiceEventProcessor implements IWorkerEventProcessor {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private static final String SERVER_READY_PATTERN = "Replica state is up to date";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String CLIENT_END_PATTERN = "Experiment ended";
	private boolean isReady;
	private boolean isEnded;

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
	public void process(String line) {
		logger.debug("{}", line);
		if (!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN))) {
			isReady = true;
		}
		if (!isEnded && line.contains(CLIENT_END_PATTERN)) {
			isEnded = true;
		}
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public boolean ended() {
		return isEnded;
	}
}
