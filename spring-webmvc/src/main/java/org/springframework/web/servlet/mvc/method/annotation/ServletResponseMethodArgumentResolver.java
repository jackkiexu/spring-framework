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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves response-related method argument values of types:
 * <ul>
 * <li>{@link ServletResponse}
 * <li>{@link OutputStream}
 * <li>{@link Writer}
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
// 针对 一些基础类的参数解决, 参数的获取一般通过 HttpServletResponse
public class ServletResponseMethodArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * 支持 ServletResponse | OutputStream | Writer 类型
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		return (ServletResponse.class.isAssignableFrom(paramType) ||
				OutputStream.class.isAssignableFrom(paramType) ||
				Writer.class.isAssignableFrom(paramType));
	}

	/**
	 * Set {@link ModelAndViewContainer#setRequestHandled(boolean)} to
	 * {@code false} to indicate that the method signature provides access
	 * to the response. If subsequently the underlying method returns
	 * {@code null}, the request is considered directly handled.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {			// 这里的解决参数, 其实就是获取 ServletHttpResponse

		if (mavContainer != null) {
			mavContainer.setRequestHandled(true);  // 标志 请求被处理过了, 视图解析就不需要了
		}
		// 获取 HttpServletResponse
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		// 获取参数的类型
		Class<?> paramType = parameter.getParameterType();
		// 若参数是 ServletResponse(这个类在 Servlet 中) 类型, 则直接通过 NativeWebRequest 来进行获取
		if (ServletResponse.class.isAssignableFrom(paramType)) {
			Object nativeResponse = webRequest.getNativeResponse(paramType);
			if (nativeResponse == null) {
				throw new IllegalStateException(
						"Current response is not of type [" + paramType.getName() + "]: " + response);
			}
			return nativeResponse;
		}
		// 若参数类型是 OutputStream, 则直接通过 HttpServletResponse 获取输出数据流
		else if (OutputStream.class.isAssignableFrom(paramType)) {
			return response.getOutputStream();
		}
		// 若参数类型是 Writer, 则直接通过 HttpServletResponse 获取 Writer
		else if (Writer.class.isAssignableFrom(paramType)) {
			return response.getWriter();
		}
		else {
			// should not happen
			Method method = parameter.getMethod();
			throw new UnsupportedOperationException("Unknown parameter type: " + paramType + " in method: " + method);
		}
	}

}
