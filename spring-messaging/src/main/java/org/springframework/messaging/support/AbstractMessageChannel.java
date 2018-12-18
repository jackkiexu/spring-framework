/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.messaging.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Abstract base class for {@link MessageChannel} implementations.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public abstract class AbstractMessageChannel implements MessageChannel, InterceptableChannel, BeanNameAware {

	protected final Log logger = LogFactory.getLog(getClass());
	// channel 的拦截器
	private final List<ChannelInterceptor> interceptors = new ArrayList<ChannelInterceptor>(5);
	// Spring 中 bean 的名称
	private String beanName;


	public AbstractMessageChannel() {
		this.beanName = getClass().getSimpleName() + "@" + ObjectUtils.getIdentityHexString(this);
	}


	/**
	 * A message channel uses the bean name primarily for logging purposes.
	 */
	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	/**
	 * Return the bean name for this message channel.
	 */
	public String getBeanName() {
		return this.beanName;
	}


	@Override
	public void setInterceptors(List<ChannelInterceptor> interceptors) {
		this.interceptors.clear();
		this.interceptors.addAll(interceptors);
	}

	@Override
	public void addInterceptor(ChannelInterceptor interceptor) {
		this.interceptors.add(interceptor);
	}

	@Override
	public void addInterceptor(int index, ChannelInterceptor interceptor) {
		this.interceptors.add(index, interceptor);
	}

	@Override
	public List<ChannelInterceptor> getInterceptors() {
		return Collections.unmodifiableList(this.interceptors);
	}

	@Override
	public boolean removeInterceptor(ChannelInterceptor interceptor) {
		return this.interceptors.remove(interceptor);
	}

	@Override
	public ChannelInterceptor removeInterceptor(int index) {
		return this.interceptors.remove(index);
	}

	// 这个 send 方法使用了默认的 超时时间
	@Override
	public final boolean send(Message<?> message) {
		return send(message, INDEFINITE_TIMEOUT);
	}

	@Override
	public final boolean send(Message<?> message, long timeout) {
		Assert.notNull(message, "Message must not be null");
		ChannelInterceptorChain chain = new ChannelInterceptorChain();
		boolean sent = false;
		try {
			message = chain.applyPreSend(message, this);   // 调用消息发送的前置处理器
			if (message == null) {							       // 拦截器作用: 这里通过判断 message == null, 来决定是否需要继续执行下面的流程
				return false;
			}
			sent = sendInternal(message, timeout);
			chain.applyPostSend(message, this, sent);      // 消息后续处理器
			chain.triggerAfterSendCompletion(message, this, sent, null); // 消息发送完成触发事件
			return sent;
		}
		catch (Exception ex) {
			chain.triggerAfterSendCompletion(message, this, sent, ex);
			if (ex instanceof MessagingException) {
				throw (MessagingException) ex;
			}
			throw new MessageDeliveryException(message,"Failed to send message to " + this, ex);
		}
		catch (Throwable err) {
			MessageDeliveryException ex2 =
					new MessageDeliveryException(message, "Failed to send message to " + this, err);
			chain.triggerAfterSendCompletion(message, this, sent, ex2);
			throw ex2;
		}
	}

	// 调用模版方法, 可以有同步的, 异步的, 返回值是否有 等等实现
	protected abstract boolean sendInternal(Message<?> message, long timeout);


	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + this.beanName + "]";
	}


	/**
	 * Assists with the invocation of the configured channel interceptors.
	 */
	protected class ChannelInterceptorChain {

		private int sendInterceptorIndex = -1;

		private int receiveInterceptorIndex = -1;

		public Message<?> applyPreSend(Message<?> message, MessageChannel channel) {
			Message<?> messageToUse = message;
			for (ChannelInterceptor interceptor : interceptors) {
				Message<?> resolvedMessage = interceptor.preSend(messageToUse, channel);
				if (resolvedMessage == null) {						// 这里 resolvedMessage == null 说明前置处理器处理后, 不需要执行后面的消息发送, 所以才执行 triggerAfterSendCompletion
					String name = interceptor.getClass().getSimpleName();
					if (logger.isDebugEnabled()) {
						logger.debug(name + " returned null from preSend, i.e. precluding the send.");
					}
					triggerAfterSendCompletion(messageToUse, channel, false, null); // 若 resolvedMessage == null, 则触发完成事件
					return null;
				}
				messageToUse = resolvedMessage;
				this.sendInterceptorIndex++;
			}
			return messageToUse;
		}
		// 发送消息成功后的后置处理
		public void applyPostSend(Message<?> message, MessageChannel channel, boolean sent) {
			for (ChannelInterceptor interceptor : interceptors) {
				interceptor.postSend(message, channel, sent);
			}
		}
		// 发送后置处理器
		public void triggerAfterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
			for (int i = this.sendInterceptorIndex; i >= 0; i--) {
				ChannelInterceptor interceptor = interceptors.get(i);
				try {
					interceptor.afterSendCompletion(message, channel, sent, ex);
				}
				catch (Throwable ex2) {
					logger.error("Exception from afterSendCompletion in " + interceptor, ex2);
				}
			}
		}

		public boolean applyPreReceive(MessageChannel channel) {
			for (ChannelInterceptor interceptor : interceptors) {
				if (!interceptor.preReceive(channel)) {
					triggerAfterReceiveCompletion(null, channel, null);
					return false;
				}
				this.receiveInterceptorIndex++;
			}
			return true;
		}

		public Message<?> applyPostReceive(Message<?> message, MessageChannel channel) {
			for (ChannelInterceptor interceptor : interceptors) {
				message = interceptor.postReceive(message, channel);
				if (message == null) {
					return null;
				}
			}
			return message;
		}

		public void triggerAfterReceiveCompletion(Message<?> message, MessageChannel channel, Exception ex) {
			for (int i = this.receiveInterceptorIndex; i >= 0; i--) {
				ChannelInterceptor interceptor = interceptors.get(i);
				try {
					interceptor.afterReceiveCompletion(message, channel, ex);
				}
				catch (Throwable ex2) {
					if (logger.isErrorEnabled()) {
						logger.error("Exception from afterReceiveCompletion in " + interceptor, ex2);
					}
				}
			}
		}
	}

}
