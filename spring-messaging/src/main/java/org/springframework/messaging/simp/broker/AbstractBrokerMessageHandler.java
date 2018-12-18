/*
 * Copyright 2002-2014 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.InterceptableChannel;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 *
 * Abstract base class for a {@link MessageHandler} that broker messages to
 * registered subscribers.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractBrokerMessageHandler implements MessageHandler, ApplicationEventPublisherAware, SmartLifecycle {

	protected final Log logger = LogFactory.getLog(getClass());

	// 提供订阅功能的 MessageChannel
	private final SubscribableChannel clientInboundChannel;

	// Message 的 channel
	private final MessageChannel clientOutboundChannel;

	// 提供订阅功能的 MessageChannel
	private final SubscribableChannel brokerChannel;

	// 目的地前缀
	private final Collection<String> destinationPrefixes;

	// 程序事件发布器
	private ApplicationEventPublisher eventPublisher;

	// Broker 是否 OK
	private AtomicBoolean brokerAvailable = new AtomicBoolean(false);

	// message broker 是否可用事件
	private final BrokerAvailabilityEvent availableEvent = new BrokerAvailabilityEvent(true, this);

	// message broker 是否可用事件
	private final BrokerAvailabilityEvent notAvailableEvent = new BrokerAvailabilityEvent(false, this);

	// 是否自动启动标示
	private boolean autoStartup = true;

	// Broker 是否在运行
	private volatile boolean running = false;

	// 生命周期监视器
	private final Object lifecycleMonitor = new Object();

	private final ChannelInterceptor unsentDisconnectInterceptor = new UnsentDisconnectChannelInterceptor();


	/**
	 * Constructor with no destination prefixes (matches all destinations).
	 * @param inboundChannel the channel for receiving messages from clients (e.g. WebSocket clients)
	 * @param outboundChannel the channel for sending messages to clients (e.g. WebSocket clients)
	 * @param brokerChannel the channel for the application to send messages to the broker
	 */
	public AbstractBrokerMessageHandler(SubscribableChannel inboundChannel, MessageChannel outboundChannel,
			SubscribableChannel brokerChannel) {

		this(inboundChannel, outboundChannel, brokerChannel, Collections.<String>emptyList());
	}

	/**
	 * Constructor with destination prefixes to match to destinations of messages.
	 * @param inboundChannel the channel for receiving messages from clients (e.g. WebSocket clients)
	 * @param outboundChannel the channel for sending messages to clients (e.g. WebSocket clients)
	 * @param brokerChannel the channel for the application to send messages to the broker
	 * @param destinationPrefixes prefixes to use to filter out messages
	 */
	public AbstractBrokerMessageHandler(SubscribableChannel inboundChannel, MessageChannel outboundChannel,
			SubscribableChannel brokerChannel, Collection<String> destinationPrefixes) {

		Assert.notNull(inboundChannel, "'inboundChannel' must not be null");
		Assert.notNull(outboundChannel, "'outboundChannel' must not be null");
		Assert.notNull(brokerChannel, "'brokerChannel' must not be null");

		this.clientInboundChannel = inboundChannel;
		this.clientOutboundChannel = outboundChannel;
		this.brokerChannel = brokerChannel;

		destinationPrefixes = (destinationPrefixes != null) ? destinationPrefixes : Collections.<String>emptyList();
		this.destinationPrefixes = Collections.unmodifiableCollection(destinationPrefixes);
	}


	public SubscribableChannel getClientInboundChannel() {
		return this.clientInboundChannel;
	}

	public MessageChannel getClientOutboundChannel() {
		return this.clientOutboundChannel;
	}

	public SubscribableChannel getBrokerChannel() {
		return this.brokerChannel;
	}

	public Collection<String> getDestinationPrefixes() {
		return this.destinationPrefixes;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.eventPublisher = publisher;
	}

	public ApplicationEventPublisher getApplicationEventPublisher() {
		return this.eventPublisher;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	// 是否是自动启动
	@Override
	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}


	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			if (logger.isInfoEnabled()) {
				logger.info("Starting...");
			}
			// client/broker subscribe 这个 messageHandler
			this.clientInboundChannel.subscribe(this);
			this.brokerChannel.subscribe(this);
			if (this.clientInboundChannel instanceof InterceptableChannel) {
				((InterceptableChannel) this.clientInboundChannel).addInterceptor(0, this.unsentDisconnectInterceptor);
			}
			// 模版方法, 留给子类进行实现
			startInternal();
			this.running = true;
			logger.info("Started.");
		}
	}

	protected void startInternal() {
	}

	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			if (logger.isInfoEnabled()) {
				logger.info("Stopping...");
			}
			// 模版方法, 用于子类实现
			stopInternal();
			// in/broker 的channel 都 unsubscrible 这个 messageHandler
			this.clientInboundChannel.unsubscribe(this);
			this.brokerChannel.unsubscribe(this);
			if (this.clientInboundChannel instanceof InterceptableChannel) {
				((InterceptableChannel) this.clientInboundChannel).removeInterceptor(this.unsentDisconnectInterceptor);
			}
			this.running = false;
			logger.info("Stopped.");
		}
	}

	protected void stopInternal() {
	}

	// 停止 MessageHandler, 并且传入了一个 callback
	@Override
	public final void stop(Runnable callback) {
		synchronized (this.lifecycleMonitor) {
			stop();
			callback.run();
		}
	}

	/**
	 * 检查 messageHandler 当前是否在 running
	 * Check whether this message handler is currently running.
	 * <p>Note that even when this message handler is running the
	 * {@link #isBrokerAvailable()} flag may still independently alternate between
	 * being on and off depending on the concrete sub-class implementation.
	 */
	@Override
	public final boolean isRunning() {
		synchronized (this.lifecycleMonitor) {
			return this.running;
		}
	}

	/**
	 * 是否当前的 message broker 是可用的, 并且能处理消息
	 * Whether the message broker is currently available and able to process messages.
	 * <p>Note that this is in addition to the {@link #isRunning()} flag, which
	 * messageHandler 必须先是 running 的
	 * indicates whether this message handler is running. In other words the message
	 * 子类 broker 是否 running 决定于下面子类的实现
	 * handler must first be running and then the {@code #isBrokerAvailable()} flag
	 * may still independently alternate between being on and off depending on the
	 * concrete sub-class implementation.
	 * <p>Application components may implement
	 * {@code org.springframework.context.ApplicationListener&lt;BrokerAvailabilityEvent&gt;}
	 * to receive notifications when broker becomes available and unavailable.
	 */
	public boolean isBrokerAvailable() {
		return this.brokerAvailable.get();
	}

	// 对消息的处理
	@Override
	public void handleMessage(Message<?> message) {
		if (!this.running) {
			if (logger.isTraceEnabled()) {
				logger.trace(this + " not running yet. Ignoring " + message);
			}
			return;
		}
		// 模版模式, 将消息处理留给子类进行实现
		handleMessageInternal(message);
	}

	protected abstract void handleMessageInternal(Message<?> message);

	// 通过 Destination 的前缀来进行判断
	protected boolean checkDestinationPrefix(String destination) {
		if ((destination == null) || CollectionUtils.isEmpty(this.destinationPrefixes)) {
			return true;
		}
		for (String prefix : this.destinationPrefixes) {
			if (destination.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	// 发布 BrokerAvailableEvent 事件
	protected void publishBrokerAvailableEvent() {
		boolean shouldPublish = this.brokerAvailable.compareAndSet(false, true);
		if (this.eventPublisher != null && shouldPublish) {
			if (logger.isInfoEnabled()) {
				logger.info(this.availableEvent);
			}
			this.eventPublisher.publishEvent(this.availableEvent);
		}
	}

	// 发布 BrokerUnavailableEvent 事件
	protected void publishBrokerUnavailableEvent() {
		boolean shouldPublish = this.brokerAvailable.compareAndSet(true, false);
		if (this.eventPublisher != null && shouldPublish) {
			if (logger.isInfoEnabled()) {
				logger.info(this.notAvailableEvent);
			}
			this.eventPublisher.publishEvent(this.notAvailableEvent);
		}
	}


	/**
	 * 在获取到 DISCONNECT 后进行消息的处理
	 * Detect unsent DISCONNECT messages and process them anyway.
	 */
	private class UnsentDisconnectChannelInterceptor extends ChannelInterceptorAdapter {

		@Override
		public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
			if (!sent) {
				SimpMessageType messageType = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
				if (SimpMessageType.DISCONNECT.equals(messageType)) {
					logger.debug("Detected unsent DISCONNECT message. Processing anyway.");
					handleMessage(message);  // 这个 handleMessage 完全可以通过在 UnsentDisconnectChannelInterceptor 中引用 MessageHandler 来处理
				}
			}
		}
	}

}
