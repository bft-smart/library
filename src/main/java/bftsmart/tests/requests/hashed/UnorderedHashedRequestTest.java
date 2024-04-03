package bftsmart.tests.requests.hashed;

import bftsmart.tests.requests.AbstractRequestTest;

public class UnorderedHashedRequestTest extends AbstractRequestTest {
	public UnorderedHashedRequestTest(String workingDirectory, int f, boolean isBFT) {
		super(workingDirectory, f, isBFT);
	}

	@Override
	public String getRequestClientClassName() {
		return "bftsmart.tests.requests.hashed.UnorderedHashedClient";
	}
}