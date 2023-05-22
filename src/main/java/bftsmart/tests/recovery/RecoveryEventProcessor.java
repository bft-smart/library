package bftsmart.tests.recovery;

import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

public class RecoveryEventProcessor implements IWorkerEventProcessor {
	private static final String SERVER_READY_PATTERN = "Ready to process operations";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private boolean isReady;

	@Override
	public void process(String line) {
		//System.out.println(line);
		if(!isReady && (line.contains(SERVER_READY_PATTERN) || line.contains(CLIENT_READY_PATTERN)))
			isReady = true;
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
