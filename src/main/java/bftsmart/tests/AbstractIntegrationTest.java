package bftsmart.tests;

import bftsmart.tests.util.ProcessExecutor;
import controller.BenchmarkControllerStartup;

import java.io.File;
import java.util.Map;
import java.util.Properties;

public abstract class AbstractIntegrationTest {

	public AbstractIntegrationTest() {}

	public void executeTest() throws Exception {
		Properties testParameters = getTestParameters();
		Properties properties = new Properties();
		for (Map.Entry<Object, Object> entry : testParameters.entrySet()) {
			properties.setProperty((String) entry.getKey(), (String) entry.getValue());
		}
		
		String controllerIp = getControllerIP();
		int controllerPort = getControllerPort();
		int nWorkers = getNWorkers();
		String workingDirectory = getWorkingDirectory();
		String workerSetupClassName = getWorkerSetupClassName();
		String benchmarkStrategyClassName = getBenchmarkStrategyClassName();
		String workerEventProcessClassName = getWorkerEventProcessClassName();
		properties.setProperty("controller.listening.ip", controllerIp);
		properties.setProperty("controller.listening.port", String.valueOf(controllerPort));
		properties.setProperty("global.worker.machines", String.valueOf(nWorkers));
		properties.setProperty("controller.benchmark.strategy", benchmarkStrategyClassName);
		properties.setProperty("controller.worker.setup", workerSetupClassName);
		properties.setProperty("controller.worker.processor", workerEventProcessClassName);

		String fileSeparator = File.separator;
		String path = "lib" + fileSeparator + "*";
		String workerCommand = String.format("java -cp %s -Dlogback.configurationFile=config\\logback.xml " +
						"worker.WorkerStartup %s %d", path, controllerIp,
				controllerPort);

		//Starting controller
		BenchmarkControllerStartup.startController(properties);
		Thread.sleep(3000);

		//Starting workers
		ProcessExecutor[] workers = new ProcessExecutor[nWorkers];
		for (int i = 0; i < nWorkers; i++) {
			String currentWorkingDirectory = String.format("%s%sworker%d%s", workingDirectory, fileSeparator, i,
					fileSeparator);
			workers[i] = new ProcessExecutor(currentWorkingDirectory, workerCommand, false);
			workers[i].start();
		}

		for (ProcessExecutor worker : workers) {
			worker.join();
		}
	}

	public abstract String getControllerIP();
	public abstract int getControllerPort();
	public abstract int getNWorkers();
	public abstract String getWorkingDirectory();
	public abstract String getBenchmarkStrategyClassName();
	public abstract String getWorkerSetupClassName();
	public abstract String getWorkerEventProcessClassName();
	public abstract Properties getTestParameters();
}
