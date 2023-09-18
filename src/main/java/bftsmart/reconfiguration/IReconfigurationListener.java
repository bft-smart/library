package bftsmart.reconfiguration;

import bftsmart.tom.core.messages.TOMMessage;

public interface IReconfigurationListener {
	void onReconfigurationRequest(TOMMessage reconfigurationRequest);
	void onReconfigurationComplete(int consensusId);
	void onReconfigurationFailure();
}
