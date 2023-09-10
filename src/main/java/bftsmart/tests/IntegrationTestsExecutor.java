package bftsmart.tests;

import bftsmart.tests.normal.CounterTest;
import bftsmart.tests.recovery.RecoveryTest;
import bftsmart.tests.requests.hashed.OrderedHashedRequestTest;
import bftsmart.tests.requests.hashed.UnorderedHashedRequestTest;
import bftsmart.tests.requests.normal.OrderedRequestTest;
import bftsmart.tests.requests.normal.UnorderedRequestTest;
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

		int nWorkers = (isBFT) ? 3 * f + 2 : 2 * f + 2;
		logger.info("Number of workers: {}", nWorkers);

		setupTestingEnvironment(workingDirectory, srcDirectory, nWorkers);

		// start integration tests
		long start, end, delta;

		AbstractIntegrationTest[] integrationTests = {
				//new RecoveryTest(workingDirectory, f, isBFT),
				new CounterTest(workingDirectory, f, isBFT),
				new OrderedRequestTest(workingDirectory, f, isBFT),
				new UnorderedRequestTest(workingDirectory, f, isBFT),
				new OrderedHashedRequestTest(workingDirectory, f, isBFT),
				new UnorderedHashedRequestTest(workingDirectory, f, isBFT)
		};

		for (AbstractIntegrationTest integrationTest : integrationTests) {
			logger.info("===== Starting {} integration test =====", integrationTest.getClass().getSimpleName());
			start = System.nanoTime();
			integrationTest.executeTest();
			end = System.nanoTime();
			delta = (end - start) / 1_000_000_000;
			logger.info("Took {} seconds to run {} integration test", delta, integrationTest.getClass().getSimpleName());
		}

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
