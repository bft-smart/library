package bftsmart.tests.normal;

import bftsmart.tom.core.messages.TOMMessageType;

public class AsynchronousNormalTest extends AbstractNormalTest {

	public AsynchronousNormalTest(String workingDirectory, int f, boolean isBFT, boolean isUnorderedRequestEnabled, TOMMessageType requestType) {
		super(workingDirectory, f, isBFT, isUnorderedRequestEnabled, requestType);
	}

	public String getClientClass() {
		return "bftsmart.tests.normal.AsyncRequestClient";
	}
}
