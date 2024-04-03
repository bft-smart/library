package bftsmart.tests.requests.hashed;

import bftsmart.tests.requests.AbstractRequestTest;

public class OrderedHashedRequestTest extends AbstractRequestTest {
	public OrderedHashedRequestTest(String workingDirectory, int f, boolean isBFT) {
		super(workingDirectory, f, isBFT);
	}

	@Override
	public String getRequestClientClassName() {
		return "bftsmart.tests.requests.hashed.OrderedHashedClient";
	}
}