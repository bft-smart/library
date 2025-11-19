package bftsmart.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.ISetupWorker;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BenchmarkSetup implements ISetupWorker {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");

	@Override
	public void setup(String setupInformation) {
		String currentViewFile = "config/currentView";
		try {
			Files.deleteIfExists(Paths.get(currentViewFile));
		} catch (IOException e) {
			logger.error("Failed to delete currentView file", e);
		}

		String[] args = setupInformation.split("\t");
		boolean isBFT = Boolean.parseBoolean(args[0]);
		int f = Integer.parseInt(args[1]);
		String hosts = args[2];
		String isUnorderedRequestEnabled = args[3];
		int nServers = (isBFT ? 3*f+1 : 2*f+1);

		logger.debug("Creating hosts.config");
		writeHosts(hosts);

		logger.debug("Creating system.config");
		String ctx=createSystemConf(nServers, args[1], args[0], isUnorderedRequestEnabled);
		writeSystemConfig(ctx);

	}

	private void writeHosts(String servers){
		String fName = "config/hosts.config";
		try(FileWriter myWriter = new FileWriter(fName)){
			String[] ips = servers.split(" ");
			String localHostIp = "127.0.0.1";
			String ports;
			for(int i=0; i<ips.length;i++){
				String ip = ips[i];
				if (ip.equals(localHostIp)) {
					ports = (11000 + i * 10) + " " + (11001 + i * 10);
				} else {
					ports = "11000 11001";
				}
				myWriter.write(i + " " + ip + " " + ports + "\n");
			}

			myWriter.write("7001 127.0.0.1 20000");
			logger.debug("Successfully wrote to the file.");
		}catch (IOException e) {
			logger.error("An error occurred.", e);
		}
	}

	private void writeSystemConfig(String content){
		String fName = "config/system.config";
		try(FileWriter myWriter = new FileWriter(fName)){
			myWriter.write(content);
			logger.debug("Successfully wrote to the system file.");
		}catch (IOException e) {
			logger.error("An error occurred.", e);
		}
	}

	private String createSystemConf(int nServers, String f, String bft, String isUnorderedRequestEnabled){
		StringBuilder viewBuilder = new StringBuilder();
		for(int i = 0; i < nServers; i++){
			viewBuilder.append(i).append(",");
		}
		String view = viewBuilder.substring(0, viewBuilder.length() - 1);

		StringBuilder ctx = new StringBuilder();
		ctx.append("system.communication.secretKeyAlgorithm = PBKDF2WithHmacSHA1\n");
		ctx.append("system.communication.secretKeyAlgorithmProvider = SunJCE\n");
		ctx.append("system.communication.hashAlgorithm = SHA-256\n");
		ctx.append("system.communication.hashAlgorithmProvider = SUN\n");
		ctx.append("system.communication.signatureAlgorithm = SHA256withECDSA\n");
		ctx.append("system.communication.signatureAlgorithmProvider = BC\n");
		ctx.append("system.communication.defaultKeyLoader = ECDSA\n");
		ctx.append("system.communication.useSenderThread = true\n");
		ctx.append("system.communication.defaultkeys = true\n");
		ctx.append("system.communication.bindaddress = auto\n");
		ctx.append("system.servers.num = ").append(nServers).append("\n");
		ctx.append("system.servers.f = ").append(f).append("\n");
		ctx.append("system.totalordermulticast.timeout = 30000\n");
		ctx.append("system.totalordermulticast.batchtimeout = -1\n");
		ctx.append("system.totalordermulticast.maxbatchsize = 1024\n");
		ctx.append("system.totalordermulticast.maxBatchSizeInBytes = 100000000\n");
		ctx.append("system.communication.useControlFlow = 1000\n");
		ctx.append("system.communication.maxRequestSize = 1000000\n");
		ctx.append("system.totalordermulticast.fairbatch = false\n");
		ctx.append("system.totalordermulticast.nonces = 10\n");
		ctx.append("system.totalordermulticast.verifyTimestamps = false\n");
		ctx.append("system.communication.inQueueSize = 500000\n");
		ctx.append("system.communication.outQueueSize = 500000\n");
		ctx.append("system.communication.useSignatures = 0\n");
		ctx.append("system.shutdownhook = true\n");
		ctx.append("system.samebatchsize = false\n");
		ctx.append("system.numrepliers = 16\n");
		ctx.append("system.totalordermulticast.state_transfer = true\n");
		ctx.append("system.totalordermulticast.highMark = 10000\n");
		ctx.append("system.totalordermulticast.revival_highMark = 10\n");
		ctx.append("system.totalordermulticast.timeout_highMark = 200\n");
		ctx.append("system.totalordermulticast.log = true\n");
		ctx.append("system.totalordermulticast.log_parallel = false\n");
		ctx.append("system.totalordermulticast.log_to_disk = false\n");
		ctx.append("system.totalordermulticast.sync_log = false\n");
		ctx.append("system.totalordermulticast.checkpoint_period = 100\n");
		ctx.append("system.totalordermulticast.global_checkpoint_period = 100\n");
		ctx.append("system.totalordermulticast.checkpoint_to_disk = false\n");
		ctx.append("system.totalordermulticast.sync_ckp = false\n");
		ctx.append("system.initial.view = ").append(view).append("\n");
		ctx.append("system.ttp.id = 7002\n");
		ctx.append("system.bft = ").append(bft).append("\n");
		ctx.append("system.ssltls.protocol_version = TLSv1.2\n");
		ctx.append("system.ssltls.key_store_file=EC_KeyPair_256.pkcs12\n");
		ctx.append("system.ssltls.enabled_ciphers = TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,\n");
		ctx.append("system.client.invokeOrderedTimeout = 40\n");
		ctx.append("system.optimizations.readonly_requests = ").append(isUnorderedRequestEnabled).append("\n");

		return ctx.toString();
	}
}
