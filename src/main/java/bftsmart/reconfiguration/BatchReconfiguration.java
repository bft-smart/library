package bftsmart.reconfiguration;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.TOMUtil;

/**
 * @author robin
 */
public class BatchReconfiguration {
	private ServiceProxy proxy;
	private final int id;
	private final KeyLoader keyLoader;
	private final String configDir;
	private final BatchReconfigurationRequest request;

	public BatchReconfiguration(int id, String configDir, KeyLoader keyLoader) {
		this.id = id;
		this.keyLoader = keyLoader;
		this.configDir = configDir;
		this.request = new BatchReconfigurationRequest(id);
	}

	public void connect() {
		if (proxy == null)
			proxy = new ServiceProxy(id, configDir, null, null, keyLoader);
	}

	public void addServer(int id, String ip, int port, int portRR) {
		request.addServer(id + ":" + ip + ":" + port + ":" + portRR);
	}

	public void removeServer(int id) {
		request.removeServer(id);
	}

	public void setF(int f) {
		request.setF(f);
	}

	public ReconfigureReply execute(byte metadata) {
		byte[] signature = TOMUtil.signMessage(proxy.getViewManager().getStaticConf().getPrivateKey(),
				request.toString().getBytes());
		request.setSignature(signature);
		byte[] serializedRequest = TOMUtil.getBytes(request);
		byte[] reply = proxy.invoke(TOMMessageType.RECONFIG, serializedRequest, null, metadata);
		request.clear();
		return (ReconfigureReply) TOMUtil.getObject(reply);
	}

	public void close() {
		proxy.close();
		proxy = null;
	}
}
