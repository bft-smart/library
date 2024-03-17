package bftsmart.tom.core.response;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingQueue;

public class ReplyThread extends Thread {
	private final Logger logger = LoggerFactory.getLogger("bftsmart");

	private final ServerCommunicationSystem cs;
	private final IMessageSentListener messageSentListener;
	private final LinkedBlockingQueue<MessageHolder> messages;
	private final MessageDigest messageDigest;

	public ReplyThread(ServerCommunicationSystem cs, IMessageSentListener messageSentListener) {
		this.cs = cs;
		this.messageSentListener = messageSentListener;
		this.messages = new LinkedBlockingQueue<>();
		try {
			this.messageDigest = TOMUtil.getHashEngine();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public void send(TOMMessage msg, int receiver, boolean isHashed) {
		try {
			messages.put(new MessageHolder(msg, receiver, isHashed));
			logger.debug("[Thread {}] Queue size: {}", this.getId(), messages.size());
		} catch (InterruptedException e) {
			logger.error("Failed to add message to send queue", e);
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				MessageHolder messageHolder = messages.take();
				TOMMessage message = messageHolder.getMessage();
				if (messageHolder.isHashed() && message.fullCommonContent == null) {
					byte[] hashedCommonContent = messageDigest.digest(message.getCommonContent());
					message.fullCommonContent = message.getCommonContent();
					message.setCommonContent(hashedCommonContent);
				}

				if (message.destination == -1) {
					cs.send(new int[]{messageHolder.getReceiver()}, message);
				}
				messageSentListener.messageSent(message);
			} catch (InterruptedException e) {
				logger.error("Failed to take message from send queue", e);
				break;
			}
		}
		logger.debug("ReplyThread stopped");
	}
}
