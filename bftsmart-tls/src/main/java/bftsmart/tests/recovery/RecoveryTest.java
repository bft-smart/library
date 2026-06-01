package bftsmart.tests.recovery;

import bftsmart.tests.AbstractIntegrationTest;

import java.util.Properties;

/**
 * @author robin
 */
public class RecoveryTest extends AbstractIntegrationTest {

	private final String workingDirectory;
	private final int f;
	private final boolean isBFT;
	private final Properties testParameters;

	public RecoveryTest(String workingDirectory, int f, boolean isBFT) {
		this.workingDirectory = workingDirectory;
		this.f = f;
		this.isBFT = isBFT;
		this.testParameters = new Properties();
		testParameters.setProperty("experiment.f", String.valueOf(f));
		testParameters.setProperty("experiment.bft", Boolean.toString(isBFT));
	}

	@Override
	public String getControllerIP() {
		return "127.0.0.1";
	}

	@Override
	public int getControllerPort() {
		return 1200;
	}

	@Override
	public int getNWorkers() {
		return (isBFT) ? 3 * f + 2 : 2 * f + 2;
	}

	@Override
	public String getWorkingDirectory() {
		return workingDirectory;
	}

	@Override
	public String getBenchmarkStrategyClassName() {
		return "bftsmart.tests.recovery.RecoveryTestStrategy";
	}

	@Override
	public String getWorkerSetupClassName() {
		return "bftsmart.tests.common.BFTSMaRtSetup";
	}

	@Override
	public String getWorkerEventProcessClassName() {
		return "bftsmart.tests.recovery.RecoveryEventProcessor";
	}

	@Override
	public Properties getTestParameters() {
		return testParameters;
	}
}
