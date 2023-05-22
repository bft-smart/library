package bftsmart.tests;

import bftsmart.tests.recovery.RecoveryTest;

public class IntegrationTestsExecutor {
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException("USAGE: bftsmart.tests.IntegrationTestsExecutor <working directory> <f>");
		}
		String workingDirectory = args[0];
		int f = Integer.parseInt(args[1]);
		long start, end, delta;
		System.out.println("Running recovery test");
		start = System.nanoTime();
		AbstractIntegrationTest recoveryTest = new RecoveryTest(workingDirectory, f);
		recoveryTest.executeTest();
		end = System.nanoTime();
		System.out.println("Recovery test terminated");

		delta = (end - start) / 1_000_000_000;
		System.out.printf("Took %d seconds to run recovery test\n", delta);
	}
}
