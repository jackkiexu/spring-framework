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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Handles return values of type {@link WebAsyncTask}.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
// 支持返回值是 WebAsyncTask 类型, 并通过 WebAsyncManager 中的 SimpleAsyncTaskExecutor 来进行处理
public class AsyncTaskMethodReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

	private final BeanFactory beanFactory;


	public AsyncTaskMethodReturnValueHandler(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return WebAsyncTask.class.isAssignableFrom(returnType.getParameterType());
	}

	@Override
	public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
		return (returnValue != null && returnValue instanceof WebAsyncTask);  // 支持返回值是 WebAsyncTask 类型, 并通过 WebAsyncManager 中的 SimpleAsyncTaskExecutor 来进行处理
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue == null) {
			mavContainer.setRequestHandled(true);
			return;
		}

		WebAsyncTask<?> webAsyncTask = (WebAsyncTask<?>) returnValue;
		webAsyncTask.setBeanFactory(this.beanFactory);
		// 通过 SimpleAsyncTaskExecutor 处理 WebAsyncTask
		WebAsyncUtils.getAsyncManager(webRequest).startCallableProcessing(webAsyncTask, mavContainer);
	}

}
