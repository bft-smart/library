package bftsmart.tests.requests;

import bftsmart.tests.AbstractIntegrationTest;

import java.util.Properties;

public abstract class AbstractRequestTest extends AbstractIntegrationTest {
	private final int nWorkers;
	private final String workingDirectory;
	private final Properties testParameters;

	public AbstractRequestTest(String workingDirectory, int f, boolean isBFT) {
		this.workingDirectory = workingDirectory;
		this.nWorkers = isBFT ? 3 * f + 2 : 2 * f + 2;
		this.testParameters = new Properties();
		String clientClass = getRequestClientClassName();
		testParameters.setProperty("experiment.clientClass", clientClass);
		testParameters.setProperty("experiment.f", String.valueOf(f));
		testParameters.setProperty("experiment.bft", Boolean.toString(isBFT));
	}

	public abstract String getRequestClientClassName();

	@Override
	public String getControllerIP() {
		return "127.0.0.1";
	}

	@Override
	public int getControllerPort() {
		return 12000;
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
		return "bftsmart.tests.requests.RequestsTestStrategy";
	}

	@Override
	public String getWorkerSetupClassName() {
		return "bftsmart.tests.BFTSMaRtSetup";
	}

	@Override
	public String getWorkerEventProcessClassName() {
		return "bftsmart.tests.requests.SimpleServiceEventProcessor";
	}

	@Override
	public Properties getTestParameters() {
		return testParameters;
	}
}
