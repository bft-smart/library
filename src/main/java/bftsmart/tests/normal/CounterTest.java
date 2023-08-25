package bftsmart.tests.normal;

import bftsmart.tests.AbstractIntegrationTest;

import java.util.Properties;

/**
 * @author nuria
 */
public class CounterTest extends AbstractIntegrationTest {

	private final String workingDirectory;
	private final int nWorkers;
	private final Properties testParameters;
	

	public CounterTest(String workingDirectory, int f, boolean isbft) {
		this.workingDirectory = workingDirectory;
		nWorkers = (isbft) ? 3 * f + 2 : 2 * f + 2;
		this.testParameters = new Properties();
		testParameters.setProperty("experiment.f", String.valueOf(f));
		testParameters.setProperty("experiment.bft", Boolean.toString(isbft));
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
		return nWorkers;
	}

	@Override
	public String getWorkingDirectory() {
		return workingDirectory;
	}

	@Override
	public String getBenchmarkStrategyClassName() {
		return "bftsmart.tests.normal.CounterTestStrategy";
	}

	@Override
	public String getWorkerSetupClassName() {
		return "bftsmart.tests.BFTSMaRtSetup";
	}

	@Override
	public String getWorkerEventProcessClassName() {
		return "bftsmart.tests.normal.CounterEventProcessor";
	}

	@Override
	public Properties getTestParameters() {
		return testParameters;
	}
}
