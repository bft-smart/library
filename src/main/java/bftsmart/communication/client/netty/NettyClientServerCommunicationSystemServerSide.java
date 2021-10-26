/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package bftsmart.communication.client.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;

/**
 *
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemServerSide extends SimpleChannelInboundHandler<TOMMessage>
		implements CommunicationSystemServerSide {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private RequestReceiver requestReceiver;
	private ConcurrentHashMap<Integer, NettyClientServerSession> sessionReplicaToClient;
	private ReentrantReadWriteLock rl;
	private ServerViewController controller;
	private boolean closed = false;
	private Channel mainChannel;

	// This locked seems to introduce a bottleneck and seems useless, but I cannot
	// recall why I added it
	// private ReentrantLock sendLock = new ReentrantLock();
	private NettyServerPipelineFactory serverPipelineFactory;

	/* Tulio Ribeiro */
	private static int tcpSendBufferSize = 8 * 1024 * 1024;
	private static int bossThreads = 8; /* listens and accepts on server socket; workers handle r/w I/O */
	private static int connectionBacklog = 1024; /* pending connections boss thread will queue to accept */
	private static int connectionTimeoutMsec = 40000; /* (40 seconds) */
	private PrivateKey privKey;
	/* Tulio Ribeiro */

	public NettyClientServerCommunicationSystemServerSide(ServerViewController controller) {
		try {

			this.controller = controller;
			/* Tulio Ribeiro */
			privKey = controller.getStaticConf().getPrivateKey();

			sessionReplicaToClient = new ConcurrentHashMap<>();
			rl = new ReentrantReadWriteLock();

			// Configure the server.

			serverPipelineFactory = new NettyServerPipelineFactory(this, sessionReplicaToClient, controller, rl);

			EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreads);
			EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.option(ChannelOption.SO_REUSEADDR, true).option(ChannelOption.SO_KEEPALIVE, true)
					.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_SNDBUF, tcpSendBufferSize)
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMsec)
					.option(ChannelOption.SO_BACKLOG, connectionBacklog)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(serverPipelineFactory.getDecoder());
							ch.pipeline().addLast(serverPipelineFactory.getEncoder());
							ch.pipeline().addLast(serverPipelineFactory.getHandler());
						}
					}).childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.TCP_NODELAY, true);
			String myAddress;
			String confAddress = controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId())
					.getAddress().getHostAddress();

			if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {

				myAddress = InetAddress.getLoopbackAddress().getHostAddress();

			}

			else if (controller.getStaticConf().getBindAddress().equals("")) {

				myAddress = InetAddress.getLocalHost().getHostAddress();

				// If Netty binds to the loopback address, clients will not be able to connect
				// to replicas.
				// To solve that issue, we bind to the address supplied in config/hosts.config
				// instead.
				if (InetAddress.getLoopbackAddress().getHostAddress().equals(myAddress)
						&& !myAddress.equals(confAddress)) {

					myAddress = confAddress;
				}

			} else {

				myAddress = controller.getStaticConf().getBindAddress();
			}

			int myPort = controller.getStaticConf().getPort(controller.getStaticConf().getProcessId());

			ChannelFuture f = b.bind(new InetSocketAddress(myAddress, myPort)).sync();

			logger.info("ID = " + controller.getStaticConf().getProcessId());
			logger.info("N = " + controller.getCurrentViewN());
			logger.info("F = " + controller.getCurrentViewF());
			logger.info("Port (client <-> server) = "
					+ controller.getStaticConf().getPort(controller.getStaticConf().getProcessId()));
			logger.info("Port (server <-> server) = "
					+ controller.getStaticConf().getServerToServerPort(controller.getStaticConf().getProcessId()));
			logger.info("requestTimeout = " + controller.getStaticConf().getRequestTimeout());
			logger.info("maxBatch = " + controller.getStaticConf().getMaxBatchSize());
			if(controller.getStaticConf().getUseSignatures() == 1) logger.info("Using Signatures");
                        else if (controller.getStaticConf().getUseSignatures() == 2) logger.info("Using benchmark signature verification");
			logger.info("Binded replica to IP address " + myAddress);
			// ******* EDUARDO END **************//

			/* Tulio Ribeiro */
			// SSL/TLS
			logger.info("SSL/TLS enabled, protocol version: {}", controller.getStaticConf().getSSLTLSProtocolVersion());

			/* Tulio Ribeiro END */

			mainChannel = f.channel();

		} catch (InterruptedException | UnknownHostException ex) {
			logger.error("Failed to create Netty communication system", ex);
		}
	}

	private void closeChannelAndEventLoop(Channel c) {
		c.flush();
		c.deregister();
		c.close();
		c.eventLoop().shutdownGracefully();
	}

	@Override
	public void shutdown() {

		logger.info("Shutting down Netty system");

		this.closed = true;

		closeChannelAndEventLoop(mainChannel);

		rl.readLock().lock();
		ArrayList<NettyClientServerSession> sessions = new ArrayList<>(sessionReplicaToClient.values());
		rl.readLock().unlock();
		for (NettyClientServerSession ncss : sessions) {

			closeChannelAndEventLoop(ncss.getChannel());

		}

		logger.info("NettyClientServerCommunicationSystemServerSide is halting.");

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

		if (this.closed) {
			closeChannelAndEventLoop(ctx.channel());
			return;
		}

		if (cause instanceof ClosedChannelException)
			logger.info("Client connection closed.");
		else if (cause instanceof IOException) {
			logger.error("Impossible to connect to client. (Connection reset by peer)");
		} else {
			logger.error("Connection problem. Cause:{}", cause);
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TOMMessage sm) throws Exception {

		if (this.closed) {
			closeChannelAndEventLoop(ctx.channel());
			return;
		}

		// delivers message to TOMLayer
		if (requestReceiver == null)
			logger.warn("Request receiver is still null!");
		else
			requestReceiver.requestReceived(sm);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {

		if (this.closed) {
			closeChannelAndEventLoop(ctx.channel());
			return;
		}
		logger.info("Session Created, active clients=" + sessionReplicaToClient.size());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		logger.debug("Channel Inactive");
		if (this.closed) {
			closeChannelAndEventLoop(ctx.channel());
			return;
		}

		// debugSessions();

		Set s = sessionReplicaToClient.entrySet();
		Iterator i = s.iterator();
		while (i.hasNext()) {
			Entry m = (Entry) i.next();
			NettyClientServerSession value = (NettyClientServerSession) m.getValue();
			if (ctx.channel().equals(value.getChannel())) {
				int key = (Integer) m.getKey();
				toRemove(key);
				break;
			}
		}

		logger.debug("Session Closed, active clients=" + sessionReplicaToClient.size());
	}

	public synchronized void toRemove(Integer key) {

		Iterator<Integer> it = sessionReplicaToClient.keySet().iterator();
		while (it.hasNext()) {
			Integer cli = (Integer) it.next();
			logger.debug("SessionReplicaToClient: Key:{}, Value:{}", cli, sessionReplicaToClient.get(cli));
		}

		logger.debug("Removing client channel with ID = " + key);
		sessionReplicaToClient.remove(key);

	}

	@Override
	public void setRequestReceiver(RequestReceiver tl) {
		this.requestReceiver = tl;
	}

	@Override
	public void send(int[] targets, TOMMessage sm, boolean serializeClassHeaders) {

		// serialize message
		DataOutputStream dos = null;

		byte[] data = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			sm.wExternal(dos);
			dos.flush();
			data = baos.toByteArray();
			sm.serializedMessage = data;
		} catch (IOException ex) {
			logger.error("Failed to serialize message.", ex);
		}

		// replies are not signed in the current JBP version
		sm.signed = false;
		// produce signature if necessary (never in the current version)
		if (sm.signed) {
			byte[] signature = TOMUtil.signMessage(privKey, data);
			sm.serializedMessageSignature = signature;
		}

		for (int target : targets) {
			try {
				sm = (TOMMessage) sm.clone();
			} catch (CloneNotSupportedException ex) {
				logger.error("Failed to clone TOMMessage", ex);
				continue;
			}

			rl.readLock().lock();
			if (sessionReplicaToClient.containsKey(target)) {
				sm.destination = target;
				sessionReplicaToClient.get(target).getChannel().writeAndFlush(sm);
			} else {
				logger.debug("Client not into sessionReplicaToClient({}):{}, waiting and retrying.", target,
						sessionReplicaToClient.containsKey(target));
				/*
				 * ClientSession clientSession = new ClientSession(target, sm); new
				 * Thread(clientSession).start();
				 */
				// should I wait for the client?
			}
			rl.readLock().unlock();
		}
	}

	@Override
	public int[] getClients() {

		rl.readLock().lock();
		Set s = sessionReplicaToClient.keySet();
		int[] clients = new int[s.size()];
		Iterator it = s.iterator();
		int i = 0;
		while (it.hasNext()) {

			clients[i] = ((Integer) it.next()).intValue();
			i++;
		}

		rl.readLock().unlock();

		return clients;
	}

}
