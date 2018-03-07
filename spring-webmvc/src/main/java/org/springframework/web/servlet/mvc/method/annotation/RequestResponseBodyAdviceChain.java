/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.ControllerAdviceBean;


/**
 * Invokes {@link RequestBodyAdvice} and {@link ResponseBodyAdvice} where each
 * instance may be (and is most likely) wrapped with
 * {@link org.springframework.web.method.ControllerAdviceBean ControllerAdviceBean}.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
class RequestResponseBodyAdviceChain implements RequestBodyAdvice, ResponseBodyAdvice<Object> {

	private final List<Object> requestBodyAdvice = new ArrayList<Object>(4);

	private final List<Object> responseBodyAdvice = new ArrayList<Object>(4);


	/**
	 * Create an instance from a list of objects that are either of type
	 * {@code ControllerAdviceBean} or {@code RequestBodyAdvice}.
	 */
	public RequestResponseBodyAdviceChain(List<Object> requestResponseBodyAdvice) {
		initAdvice(requestResponseBodyAdvice);  // 初始化 Resquest | Response 增强器
	}

	private void initAdvice(List<Object> requestResponseBodyAdvice) {
		if (requestResponseBodyAdvice == null) {
			return;
		}
		for (Object advice : requestResponseBodyAdvice) {
			// 获取 advice 的类型
			Class<?> beanType = (advice instanceof ControllerAdviceBean ? ((ControllerAdviceBean) advice).getBeanType() : advice.getClass());
			// 这个是在 读取请求消息的前&&后的增强器
			if (RequestBodyAdvice.class.isAssignableFrom(beanType)) {	// 将 Advice 加入 requestBodyAdvice 中
				this.requestBodyAdvice.add(advice);
			}
			// 写入消息前的增强器
			else if (ResponseBodyAdvice.class.isAssignableFrom(beanType)) { // 将 Advice 加入 responseBodyAdvice 中
				this.responseBodyAdvice.add(advice);
			}
		}
	}

	// 获取 adviceType 类型的 增强器
	private List<Object> getAdvice(Class<?> adviceType) {
		if (RequestBodyAdvice.class == adviceType) {
			return this.requestBodyAdvice;
		}
		else if (ResponseBodyAdvice.class == adviceType) {
			return this.responseBodyAdvice;
		}
		else {
			throw new IllegalArgumentException("Unexpected adviceType: " + adviceType);
		}
	}


	@Override
	public boolean supports(MethodParameter param, Type type, Class<? extends HttpMessageConverter<?>> converterType) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
		// 对空数据进行一些增强操作
		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				body = advice.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
			}
		}
		return body;
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage request, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
		// 通过 RequestBodyAdvice 在 对 HttpServletRequest 读取的数据之前 进行一些增强操作
		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				request = advice.beforeBodyRead(request, parameter, targetType, converterType);
			}
		}
		return request;
	}

	@Override
	public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
		// 通过 RequestBodyAdvice 对从 HttpServletRequest 里面读取的数据进行一些增强操作
		for (RequestBodyAdvice advice : getMatchingAdvice(parameter, RequestBodyAdvice.class)) {
			if (advice.supports(parameter, targetType, converterType)) {
				body = advice.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
			}
		}
		return body;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
			ServerHttpRequest request, ServerHttpResponse response) {
		// 这里是在将数据写入 outputStream 之前, 进行一些增强操作
		return processBody(body, returnType, contentType, converterType, request, response);
	}

	@SuppressWarnings("unchecked")
	private <T> Object processBody(Object body, MethodParameter returnType, MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType,
			ServerHttpRequest request, ServerHttpResponse response) {

		for (ResponseBodyAdvice<?> advice : getMatchingAdvice(returnType, ResponseBodyAdvice.class)) {
			if (advice.supports(returnType, converterType)) {
				// 通过 ResponseBodyAdvice 在将数据写入输出流之前进行一些增强操作
				body = ((ResponseBodyAdvice<T>) advice).beforeBodyWrite((T) body, returnType, contentType, converterType, request, response);
			}
		}
		return body;
	}
	// 获取 Advice
	@SuppressWarnings("unchecked")
	private <A> List<A> getMatchingAdvice(MethodParameter parameter, Class<? extends A> adviceType) {
		List<Object> availableAdvice = getAdvice(adviceType);  // 获取 adviceType 类型的增强器
		if (CollectionUtils.isEmpty(availableAdvice)) {
			return Collections.emptyList();
		}
		List<A> result = new ArrayList<A>(availableAdvice.size());
		for (Object advice : availableAdvice) {
			if (advice instanceof ControllerAdviceBean) {
				ControllerAdviceBean adviceBean = (ControllerAdviceBean) advice;
				if (!adviceBean.isApplicableToBeanType(parameter.getContainingClass())) {
					continue;
				}
				advice = adviceBean.resolveBean();  // 这里的 advice 其实就是 被 @ControllerAdvice 修饰的 Bean
			}
			if (adviceType.isAssignableFrom(advice.getClass())) { // 若 advice 是 adviceType 类型, 则将 advice 加入到 result 里面
				result.add((A) advice);
			}
		}
		return result;
	}

}
