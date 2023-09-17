package bftsmart.tom;

import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.ServiceContent;
import bftsmart.tom.util.ServiceResponse;

import java.util.Comparator;
import java.util.Map;

public class ExtendedServiceProxy extends ServiceProxy {
	public ExtendedServiceProxy(int processId) {
		super(processId);
	}

	public ExtendedServiceProxy(int processId, String configHome) {
		super(processId, configHome);
	}

	public ExtendedServiceProxy(int processId, String configHome, KeyLoader loader) {
		super(processId, configHome, loader);
	}

	public ExtendedServiceProxy(int processId, String configHome, Comparator<ServiceContent> replyComparator,
								Extractor replyExtractor, KeyLoader loader) {
		super(processId, configHome, replyComparator, replyExtractor, loader);
	}

	/**
	 * This method sends an ordered request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public ServiceResponse invokeOrdered(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
	}

	/**
	 * This method sends an ordered request to the replicas, and returns the related reply.
	 * This method chooses randomly one replica to send the complete response, while the others
	 * only send a hash of that response.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public ServiceResponse invokeOrderedHashed(byte[] request, Map<Integer, byte[]> replicaSpecificContents,
											  byte metadata) {
		return invoke(TOMMessageType.ORDERED_HASHED_REQUEST, request, replicaSpecificContents, metadata);
	}

	/**
	 * This method sends an unordered request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public ServiceResponse invokeUnordered(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.UNORDERED_REQUEST, request, replicaSpecificContents, metadata);
	}

	/**
	 * This method sends an unordered request to the replicas, and returns the related reply.
	 * This method chooses randomly one replica to send the complete response, while the others
	 * only send a hash of that response.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public ServiceResponse invokeUnorderedHashed(byte[] request, Map<Integer, byte[]> replicaSpecificContents,
												byte metadata) {
		return invoke(TOMMessageType.UNORDERED_HASHED_REQUEST, request, replicaSpecificContents, metadata);
	}
}
