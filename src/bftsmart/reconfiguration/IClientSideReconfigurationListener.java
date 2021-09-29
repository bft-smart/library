package bftsmart.reconfiguration;

import bftsmart.reconfiguration.views.View;

/**
 * @author robin
 */
public interface IClientSideReconfigurationListener {
	void onReconfiguration(View view);
}
