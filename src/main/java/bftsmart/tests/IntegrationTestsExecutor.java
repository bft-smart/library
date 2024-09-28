package bftsmart.tests;

import bftsmart.tests.normal.AsynchronousNormalTest;
import bftsmart.tests.normal.SynchronousNormalTest;
import bftsmart.tests.recovery.RecoveryTest;
import bftsmart.tom.core.messages.TOMMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class IntegrationTestsExecutor {
	private static final Logger logger = LoggerFactory.getLogger("testing");

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			throw new IllegalArgumentException("USAGE: bftsmart.tests.IntegrationTestsExecutor " +
					"<working directory> <src directory> <f> <isBFT>");
		}

		String workingDirectory = args[0];
		String srcDirectory = args[1];
		int f = Integer.parseInt(args[2]);
		boolean isBFT = Boolean.parseBoolean(args[3]);

		int nWorkers = (isBFT ? 3 * f + 1 : 2 * f + 1) + 1;
		logger.info("Number of workers: {}", nWorkers);

		setupTestingEnvironment(workingDirectory, srcDirectory, nWorkers);
		long start, end, delta;
		start = System.nanoTime();
		runAllTests(workingDirectory, f, isBFT);
		end = System.nanoTime();
		delta = (end - start) / 1_000_000_000;
		long deltaMinute = delta / 60;
		logger.info("Took {} seconds ({} minutes) to run all tests", delta, deltaMinute);
	}

	private static void runAllTests(String workingDirectory, int f, boolean isBFT) throws Exception {
		logger.info("Fault tolerance: {} ({})", f, isBFT ? "BFT" : "crash");

		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, false, TOMMessageType.ORDERED_REQUEST),
				"synchronous ORDERED request without read optimization"
		);
		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.ORDERED_REQUEST),
				"synchronous ORDERED request with read optimization"
		);
		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.UNORDERED_REQUEST),
				"synchronous UNORDERED request without read optimization"
		);
		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, false, TOMMessageType.ORDERED_HASHED_REQUEST),
				"synchronous ORDERED HASHED request without read optimization"
		);
		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.ORDERED_HASHED_REQUEST),
				"synchronous ORDERED HASHED request with read optimization"
		);
		runTest(
				new SynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.UNORDERED_HASHED_REQUEST),
				"synchronous UNORDERED HASHED request with read optimization"
		);

		runTest(
				new AsynchronousNormalTest(workingDirectory, f, isBFT, false, TOMMessageType.ORDERED_REQUEST),
				"asynchronous ORDERED request without read optimization"
		);
		runTest(
				new AsynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.ORDERED_REQUEST),
				"asynchronous ORDERED request with read optimization"
		);
		runTest(
				new AsynchronousNormalTest(workingDirectory, f, isBFT, true, TOMMessageType.UNORDERED_REQUEST),
				"asynchronous UNORDERED request with read optimization"
		);

		runTest(
				new RecoveryTest(workingDirectory, f, isBFT),
				"recovery"
		);
	}

	private static void runTest(AbstractIntegrationTest test, String title) throws Exception {
		long start, end, delta;
		logger.info("Running {} test...", title);
		start = System.nanoTime();
		test.executeTest();
		end = System.nanoTime();
		delta = (end - start) / 1_000_000_000;
		logger.info("Took {} seconds", delta);
	}

	private static void setupTestingEnvironment(String workingDirectory, String sourceDirectory,
												int nWorkers) throws IOException {
		workingDirectory = Paths.get(workingDirectory).normalize().toString();
		sourceDirectory = Paths.get(sourceDirectory).normalize().toString();

		logger.info("Working directory: {}", workingDirectory);
		logger.info("Source directory: {}", sourceDirectory);

		//Prepare workers directories
		for(int i = 0; i < nWorkers; i++){
			Path workerDir = Paths.get(workingDirectory, "worker" + i);
			createDirectory(workerDir);

			copySourceFiles(Paths.get(sourceDirectory), workerDir.toString());
		}

		//Prepare controller directory
		Path controllerDir = Paths.get(workingDirectory, "controller");
		createDirectory(controllerDir);
		copySourceFiles(Paths.get(sourceDirectory), controllerDir.toString());
	}

	private static void copySourceFiles(Path sourceDir, String destinationDir) throws IOException {
		File sourceFile = sourceDir.toFile();
		File[] files = sourceFile.listFiles();
		if (files == null) //source file is a directory
			return;
		for (File file : files) {
			if (file.isDirectory()) {
				String subDir = sourceDir.relativize(file.toPath()).toString();
				Path dstPath = Paths.get(destinationDir, subDir);
				createDirectory(dstPath);
				copySourceFiles(file.toPath(), dstPath.toString());
			} else {
				Path sourceFilePath = file.toPath();
				Path targetFile = Paths.get(destinationDir, sourceFilePath.getFileName().toString());

				Files.copy(sourceFilePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static void createDirectory(Path path) throws IOException {
		if (!path.toFile().exists()) {
			Files.createDirectories(path);
		}
	}
}
