package bftsmart.tests.normal;

import bftsmart.tests.AbstractIntegrationTest;
import bftsmart.tom.core.messages.TOMMessageType;

import java.util.Properties;

public abstract class AbstractNormalTest extends AbstractIntegrationTest {
	private final int nWorkers;
	private final String workingDirectory;
	private final Properties testParameters;

	public AbstractNormalTest(String workingDirectory, int f, boolean isBFT, boolean isUnorderedRequestEnabled,
								 TOMMessageType requestType) {
		this.workingDirectory = workingDirectory;
		this.nWorkers = (isBFT ? 3 * f + 1 : 2 * f + 1) + 1;// +1 for a client
		this.testParameters = new Properties();
		String clientClass = getClientClass();
		testParameters.setProperty("experiment.clientClass", clientClass);
		testParameters.setProperty("experiment.requestType", requestType.name());
		testParameters.setProperty("experiment.f", String.valueOf(f));
		testParameters.setProperty("experiment.bft", Boolean.toString(isBFT));
		testParameters.setProperty("experiment.isUnorderedRequestEnabled", Boolean.toString(isUnorderedRequestEnabled));
	}

	public abstract String getClientClass();

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
		return "bftsmart.tests.normal.NormalRequestsTestStrategy";
	}

	@Override
	public String getWorkerSetupClassName() {
		return "bftsmart.tests.common.BFTSMaRtSetup";
	}

	@Override
	public String getWorkerEventProcessClassName() {
		return "bftsmart.tests.common.SimpleServiceEventProcessor";
	}

	@Override
	public Properties getTestParameters() {
		return testParameters;
	}
}
