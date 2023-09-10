package bftsmart.tests.requests.normal;

import bftsmart.tests.requests.AbstractRequestTest;

public class UnorderedRequestTest extends AbstractRequestTest {
	public UnorderedRequestTest(String workingDirectory, int f, boolean isBFT) {
		super(workingDirectory, f, isBFT);
	}

	@Override
	public String getRequestClientClassName() {
		return "bftsmart.tests.requests.normal.UnorderedClient";
	}
}