package bftsmart.tests;

import bftsmart.tests.recovery.RecoveryTest;

public class IntegrationTestsExecutor {
	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			throw new IllegalArgumentException("USAGE: bftsmart.tests.IntegrationTestsExecutor <working directory> <f> <isbft>");
		}
		System.out.println("aaa");
		String workingDirectory = args[0];
		int f = Integer.parseInt(args[1]);
		boolean isbft = Boolean.parseBoolean(args[2]);
		long start, end, delta;
		System.out.println("Running recovery test");
		start = System.nanoTime();
		AbstractIntegrationTest recoveryTest = new RecoveryTest(workingDirectory, f, isbft);
		recoveryTest.executeTest();
		end = System.nanoTime();
		System.out.println("Recovery test terminated");

		delta = (end - start) / 1_000_000_000;
		System.out.printf("Took %d seconds to run recovery test\n", delta);
	}
}
