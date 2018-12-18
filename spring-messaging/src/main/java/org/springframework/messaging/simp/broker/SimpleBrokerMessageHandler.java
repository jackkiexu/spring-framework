/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.simp.broker;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderInitializer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.PathMatcher;

/**
 * 1. 认别 message type
 * 2. 追踪订阅者
 * 3. 发送信息给订阅者
 *
 * A "simple" message broker that recognizes the message types defined in
 * {@link SimpMessageType}, keeps track of subscriptions with the help of a
 * {@link SubscriptionRegistry} and sends messages to subscribers.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 4.0
 */
public class SimpleBrokerMessageHandler extends AbstractBrokerMessageHandler {

	/**
	 * 1. clientInboundChannel: 从 client 端接收信息处理
	 * 2. clientOutboundChannel: 发送信息给 client 端
	 * 3. brokerChannel: 发送消息给 broker 的 channel
	 */

	// 空 payload
	private static final byte[] EMPTY_PAYLOAD = new byte[0];

	private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<String, SessionInfo>();

	// 订阅者注册器
	private SubscriptionRegistry subscriptionRegistry;
	// 路径匹配器
	private PathMatcher pathMatcher;
	// 缓存限制器
	private Integer cacheLimit;
	// 任务调度器
	private TaskScheduler taskScheduler;

	private long[] heartbeatValue;
	// 心跳的 Future
	private ScheduledFuture<?> heartbeatFuture;
	// 消息头初始器
	private MessageHeaderInitializer headerInitializer;


	/**
	 * Create a SimpleBrokerMessageHandler instance with the given message channels
	 * and destination prefixes.
	 * @param clientInboundChannel the channel for receiving messages from clients (e.g. WebSocket clients)
	 * @param clientOutboundChannel the channel for sending messages to clients (e.g. WebSocket clients)
	 * @param brokerChannel the channel for the application to send messages to the broker
	 * @param destinationPrefixes prefixes to use to filter out messages
	 */
	public SimpleBrokerMessageHandler(SubscribableChannel clientInboundChannel, MessageChannel clientOutboundChannel,
			SubscribableChannel brokerChannel, Collection<String> destinationPrefixes) {

		super(clientInboundChannel, clientOutboundChannel, brokerChannel, destinationPrefixes);
		// 创建默认的 subscription 注册器
		this.subscriptionRegistry = new DefaultSubscriptionRegistry();
	}


	/**
	 * Configure a custom SubscriptionRegistry to use for storing subscriptions.
	 * <p><strong>Note</strong> that when a custom PathMatcher is configured via
	 * {@link #setPathMatcher}, if the custom registry is not an instance of
	 * {@link DefaultSubscriptionRegistry}, the provided PathMatcher is not used
	 * and must be configured directly on the custom registry.
	 */
	public void setSubscriptionRegistry(SubscriptionRegistry subscriptionRegistry) {
		Assert.notNull(subscriptionRegistry, "SubscriptionRegistry must not be null");
		this.subscriptionRegistry = subscriptionRegistry;
		initPathMatcherToUse();
		initCacheLimitToUse();
	}

	public SubscriptionRegistry getSubscriptionRegistry() {
		return this.subscriptionRegistry;
	}

	/**
	 * When configured, the given PathMatcher is passed down to the underlying
	 * SubscriptionRegistry to use for matching destination to subscriptions.
	 * <p>Default is a standard {@link org.springframework.util.AntPathMatcher}.
	 * @since 4.1
	 * @see #setSubscriptionRegistry
	 * @see DefaultSubscriptionRegistry#setPathMatcher
	 * @see org.springframework.util.AntPathMatcher
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
		initPathMatcherToUse();
	}

	private void initPathMatcherToUse() {
		if (this.pathMatcher != null && this.subscriptionRegistry instanceof DefaultSubscriptionRegistry) {
			((DefaultSubscriptionRegistry) this.subscriptionRegistry).setPathMatcher(this.pathMatcher);
		}
	}

	/**
	 * When configured, the specified cache limit is passed down to the
	 * underlying SubscriptionRegistry, overriding any default there.
	 * <p>With a standard {@link DefaultSubscriptionRegistry}, the default
	 * cache limit is 1024.
	 * @since 4.3.2
	 * @see #setSubscriptionRegistry
	 * @see DefaultSubscriptionRegistry#setCacheLimit
	 * @see DefaultSubscriptionRegistry#DEFAULT_CACHE_LIMIT
	 */
	public void setCacheLimit(Integer cacheLimit) {
		this.cacheLimit = cacheLimit;
		initCacheLimitToUse();
	}

	private void initCacheLimitToUse() {
		if (this.cacheLimit != null && this.subscriptionRegistry instanceof DefaultSubscriptionRegistry) {
			((DefaultSubscriptionRegistry) this.subscriptionRegistry).setCacheLimit(this.cacheLimit);
		}
	}

	/**
	 * Configure the {@link org.springframework.scheduling.TaskScheduler} to
	 * use for providing heartbeat support. Setting this property also sets the
	 * {@link #setHeartbeatValue heartbeatValue} to "10000, 10000".
	 * <p>By default this is not set.
	 * @since 4.2
	 */
	public void setTaskScheduler(TaskScheduler taskScheduler) {
		Assert.notNull(taskScheduler, "TaskScheduler must not be null");
		this.taskScheduler = taskScheduler;
		if (this.heartbeatValue == null) {
			this.heartbeatValue = new long[] {10000, 10000};
		}
	}

	/**
	 * Return the configured TaskScheduler.
	 * @since 4.2
	 */
	public TaskScheduler getTaskScheduler() {
		return this.taskScheduler;
	}

	/**
	 * heart-beat 设置:
	 * 1. 服务端向 client 发送心跳
	 * 2. 客户端向 server 发送心跳时间
	 * Configure the value for the heart-beat settings. The first number
	 * represents how often the server will write or send a heartbeat.
	 * The second is how often the client should write. 0 means no heartbeats.
	 * <p>By default this is set to "0, 0" unless the {@link #setTaskScheduler
	 * taskScheduler} in which case the default becomes "10000,10000"
	 * (in milliseconds).
	 * @since 4.2
	 */
	public void setHeartbeatValue(long[] heartbeat) {
		if (heartbeat == null || heartbeat.length != 2 || heartbeat[0] < 0 || heartbeat[1] < 0) {
			throw new IllegalArgumentException("Invalid heart-beat: " + Arrays.toString(heartbeat));
		}
		this.heartbeatValue = heartbeat;
	}

	/**
	 * The configured value for the heart-beat settings.
	 * @since 4.2
	 */
	public long[] getHeartbeatValue() {
		return this.heartbeatValue;
	}

	/**
	 * 设置 message header 初始器
	 * Configure a {@link MessageHeaderInitializer} to apply to the headers
	 * of all messages sent to the client outbound channel.
	 * <p>By default this property is not set.
	 * @since 4.1
	 */
	public void setHeaderInitializer(MessageHeaderInitializer headerInitializer) {
		this.headerInitializer = headerInitializer;
	}

	/**
	 * Return the configured header initializer.
	 * @since 4.1
	 */
	public MessageHeaderInitializer getHeaderInitializer() {
		return this.headerInitializer;
	}


	@Override
	public void startInternal() {
		// 发布 BrokerAvailableEvent 事件
		publishBrokerAvailableEvent();
		if (getTaskScheduler() != null) {
			long interval = initHeartbeatTaskDelay();
			// 若心跳间隔 > 0, 则启动 心跳任务
			if (interval > 0) {
				this.heartbeatFuture = this.taskScheduler.scheduleWithFixedDelay(new HeartbeatTask(), interval);
			}
		}
		else {
			Assert.isTrue(getHeartbeatValue() == null ||
					(getHeartbeatValue()[0] == 0 && getHeartbeatValue()[1] == 0),
					"Heartbeat values configured but no TaskScheduler provided");
		}
	}

	// 获取 heartbeat delay
	private long initHeartbeatTaskDelay() {
		if (getHeartbeatValue() == null) {
			return 0;
		}
		else if (getHeartbeatValue()[0] > 0 && getHeartbeatValue()[1] > 0) {
			return Math.min(getHeartbeatValue()[0], getHeartbeatValue()[1]);
		}
		else {
			return (getHeartbeatValue()[0] > 0 ? getHeartbeatValue()[0] : getHeartbeatValue()[1]);
		}
	}

	@Override
	public void stopInternal() {
		// 发布 BrokerUnavailableEvent 事件
		publishBrokerUnavailableEvent();
		if (this.heartbeatFuture != null) {
			this.heartbeatFuture.cancel(true);
		}
	}

	@Override
	protected void handleMessageInternal(Message<?> message) {
		// 获取消息头
		MessageHeaders headers = message.getHeaders();
		// 获取消息的类别
		SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(headers);
		// 获取消息的 destination
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		// 获取 sessionId
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		// 获取用户
		Principal user = SimpMessageHeaderAccessor.getUser(headers);
		// 更新 session 的 readTime
		updateSessionReadTime(sessionId);
		// 通过 destination 来判断
		if (!checkDestinationPrefix(destination)) {
			return;
		}

		// 消息是 message 类别, 则发送给所有 subscriber
		if (SimpMessageType.MESSAGE.equals(messageType)) {
			logMessage(message);
			sendMessageToSubscribers(destination, message);
		}
		else if (SimpMessageType.CONNECT.equals(messageType)) {
			// 请求进行连接
			logMessage(message);
			// 获取 heartBeat 设置
			long[] clientHeartbeat = SimpMessageHeaderAccessor.getHeartbeat(headers);
			long[] serverHeartbeat = getHeartbeatValue();
			// 创建 session
			this.sessions.put(sessionId, new SessionInfo(sessionId, user, clientHeartbeat, serverHeartbeat));
			// 创建 messageHeader 访问器
			SimpMessageHeaderAccessor connectAck = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
			initHeaders(connectAck);
			connectAck.setSessionId(sessionId);
			connectAck.setUser(SimpMessageHeaderAccessor.getUser(headers));
			// 在 header 里面设置 信息
			connectAck.setHeader(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER, message);
			connectAck.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, serverHeartbeat);
			// 创建 message, 里面包含 EMPTY_PAYLOAD
			Message<byte[]> messageOut = MessageBuilder.createMessage(EMPTY_PAYLOAD, connectAck.getMessageHeaders());
			getClientOutboundChannel().send(messageOut);
		}
		else if (SimpMessageType.DISCONNECT.equals(messageType)) {
			// 断开连接
			logMessage(message);
			handleDisconnect(sessionId, user, message);
		}
		else if (SimpMessageType.SUBSCRIBE.equals(messageType)) {
			// 向注册中心注册订阅
			logMessage(message);
			this.subscriptionRegistry.registerSubscription(message);
		}
		else if (SimpMessageType.UNSUBSCRIBE.equals(messageType)) {
			// 从订阅中心 unregister
			logMessage(message);
			this.subscriptionRegistry.unregisterSubscription(message);
		}
	}

	private void updateSessionReadTime(String sessionId) {
		if (sessionId != null) {
			SessionInfo info = this.sessions.get(sessionId);
			if (info != null) {
				info.setLastReadTime(System.currentTimeMillis());
			}
		}
	}

	private void logMessage(Message<?> message) {
		if (logger.isDebugEnabled()) {
			SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
			accessor = (accessor != null ? accessor : SimpMessageHeaderAccessor.wrap(message));
			logger.debug("Processing " + accessor.getShortLogMessage(message.getPayload()));
		}
	}

	private void initHeaders(SimpMessageHeaderAccessor accessor) {
		if (getHeaderInitializer() != null) {
			getHeaderInitializer().initHeaders(accessor);
		}
	}

	// 断开与 client 端 session 连接
	private void handleDisconnect(String sessionId, Principal user, Message<?> origMessage) {
		this.sessions.remove(sessionId);
		this.subscriptionRegistry.unregisterAllSubscriptions(sessionId);
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT_ACK);
		accessor.setSessionId(sessionId);
		accessor.setUser(user);
		if (origMessage != null) {
			accessor.setHeader(SimpMessageHeaderAccessor.DISCONNECT_MESSAGE_HEADER, origMessage);
		}
		initHeaders(accessor);
		Message<byte[]> message = MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
		getClientOutboundChannel().send(message);
	}

	protected void sendMessageToSubscribers(String destination, Message<?> message) {
		// 通过 message 找到 subscription
		MultiValueMap<String,String> subscriptions = this.subscriptionRegistry.findSubscriptions(message);
		if (!subscriptions.isEmpty() && logger.isDebugEnabled()) {
			logger.debug("Broadcasting to " + subscriptions.size() + " sessions.");
		}
		long now = System.currentTimeMillis();
		// 循环遍历 subscription
		for (Map.Entry<String, List<String>> subscriptionEntry : subscriptions.entrySet()) {
			for (String subscriptionId : subscriptionEntry.getValue()) {
				// 获取 messageHeader 访问器
				SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
				initHeaders(headerAccessor);
				// 设置 sessionId
				headerAccessor.setSessionId(subscriptionEntry.getKey());
				// 设置 subscriptionId
				headerAccessor.setSubscriptionId(subscriptionId);
				//将发来的信息头 copy 回去
				headerAccessor.copyHeadersIfAbsent(message.getHeaders());
				// 获取 发来消息的 body
				Object payload = message.getPayload();
				// 创建 message
				Message<?> reply = MessageBuilder.createMessage(payload, headerAccessor.getMessageHeaders());
				try {
					getClientOutboundChannel().send(reply);
				}
				catch (Throwable ex) {
					logger.error("Failed to send " + message, ex);
				}
				finally {
					SessionInfo info = this.sessions.get(subscriptionEntry.getKey());
					if (info != null) {
						// 设置 writeTime
						info.setLastWriteTime(now);
					}
				}
			}
		}
	}

	@Override
	public String toString() {
		return "SimpleBrokerMessageHandler [" + this.subscriptionRegistry + "]";
	}


	private static class SessionInfo {

		/* STOMP spec: receiver SHOULD take into account an error margin */
		private static final long HEARTBEAT_MULTIPLIER = 3;

		private final String sessiondId;

		private final Principal user;

		private final long readInterval;

		private final long writeInterval;

		private volatile long lastReadTime;

		private volatile long lastWriteTime;

		public SessionInfo(String sessiondId, Principal user, long[] clientHeartbeat, long[] serverHeartbeat) {
			this.sessiondId = sessiondId;
			this.user = user;
			if (clientHeartbeat != null && serverHeartbeat != null) {
				this.readInterval = (clientHeartbeat[0] > 0 && serverHeartbeat[1] > 0 ?
						Math.max(clientHeartbeat[0], serverHeartbeat[1]) * HEARTBEAT_MULTIPLIER : 0);
				this.writeInterval = (clientHeartbeat[1] > 0 && serverHeartbeat[0] > 0 ?
						Math.max(clientHeartbeat[1], serverHeartbeat[0]) : 0);
			}
			else {
				this.readInterval = 0;
				this.writeInterval = 0;
			}
			this.lastReadTime = this.lastWriteTime = System.currentTimeMillis();
		}

		public String getSessiondId() {
			return this.sessiondId;
		}

		public Principal getUser() {
			return this.user;
		}

		public long getReadInterval() {
			return this.readInterval;
		}

		public long getWriteInterval() {
			return this.writeInterval;
		}

		public long getLastReadTime() {
			return this.lastReadTime;
		}

		public void setLastReadTime(long lastReadTime) {
			this.lastReadTime = lastReadTime;
		}

		public long getLastWriteTime() {
			return this.lastWriteTime;
		}

		public void setLastWriteTime(long lastWriteTime) {
			this.lastWriteTime = lastWriteTime;
		}
	}


	private class HeartbeatTask implements Runnable {

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			// 获取所有的 client sessions
			for (SessionInfo info : sessions.values()) {
				// 若 read 间隔设置大于0, 则检测 现在 - 上次channel读取的时间 > 读取间隔
				if (info.getReadInterval() > 0 && (now - info.getLastReadTime()) > info.getReadInterval()) {
					// 断开连接
					handleDisconnect(info.getSessiondId(), info.getUser(), null);
				}
				// 若 write 间隔设置 > 0, 则检测 现在 - 上次写的时间 > 写间隔
				if (info.getWriteInterval() > 0 && (now - info.getLastWriteTime()) > info.getWriteInterval()) {
					// 创建 messageHeader 访问器
					SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.HEARTBEAT);
					accessor.setSessionId(info.getSessiondId());
					accessor.setUser(info.getUser());
					// 初始化 message header accessor
					initHeaders(accessor);
					// 获取 message headers
					MessageHeaders headers = accessor.getMessageHeaders();
					// 发送 心跳信息
					getClientOutboundChannel().send(MessageBuilder.createMessage(EMPTY_PAYLOAD, headers));
				}
			}
		}
	}

}
