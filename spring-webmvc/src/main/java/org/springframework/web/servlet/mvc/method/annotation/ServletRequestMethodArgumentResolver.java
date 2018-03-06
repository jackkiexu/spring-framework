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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.InputStream;
import java.io.Reader;
import java.security.Principal;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.lang.UsesJava8;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Resolves request-related method argument values of the following types:
 * <ul>
 * <li>{@link WebRequest}
 * <li>{@link ServletRequest}
 * <li>{@link MultipartRequest}
 * <li>{@link HttpSession}
 * <li>{@link Principal}
 * <li>{@link InputStream}
 * <li>{@link Reader}
 * <li>{@link HttpMethod} (as of Spring 4.0)
 * <li>{@link Locale}
 * <li>{@link TimeZone} (as of Spring 4.0)
 * <li>{@link java.time.ZoneId} (as of Spring 4.0 and Java 8)
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
// 针对 一些基础类的参数解决, 参数的获取一般通过 HttpServletRequest|LocaleResolver
public class ServletRequestMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		/** 解决下面这些类型的参数
		 *  1. WebRequest
		 *  2. ServletRequest
		 *  3. MultipartRequest
		 *  4. Principal
		 *  5. InputStream
		 *  6. Reader
		 *  7. HttpMethod
		 *  8. Locale
		 *  9. TimeZone
		 *  10. ZoneId
		 */
		return (WebRequest.class.isAssignableFrom(paramType) ||
				ServletRequest.class.isAssignableFrom(paramType) ||
				MultipartRequest.class.isAssignableFrom(paramType) ||
				HttpSession.class.isAssignableFrom(paramType) ||
				Principal.class.isAssignableFrom(paramType) ||
				InputStream.class.isAssignableFrom(paramType) ||
				Reader.class.isAssignableFrom(paramType) ||
				HttpMethod.class == paramType ||
				Locale.class == paramType ||
				TimeZone.class == paramType ||
				"java.time.ZoneId".equals(paramType.getName()));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {		// 这里其实就是从 webRequest 里面获取 Support 的那几种类型的数据
		// 获取参数的类型
		Class<?> paramType = parameter.getParameterType();
		// 若参数类型是 WebRequest, 则直接返回 NativeWebRequest
		if (WebRequest.class.isAssignableFrom(paramType)) {
			if (!paramType.isInstance(webRequest)) {
				throw new IllegalStateException(
						"Current request is not of type [" + paramType.getName() + "]: " + webRequest);
			}
			return webRequest;
		}
		// 从 NativeWebRequest 中获取 HttpServletRequest
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		// 若 参数是 ServletRequest | MultipartRequest, 则直接从 NativeWebRequest 中获取
		if (ServletRequest.class.isAssignableFrom(paramType) || MultipartRequest.class.isAssignableFrom(paramType)) {
			Object nativeRequest = webRequest.getNativeRequest(paramType);
			if (nativeRequest == null) {
				throw new IllegalStateException(
						"Current request is not of type [" + paramType.getName() + "]: " + request);
			}
			return nativeRequest;
		}
		// 若参数是 HttpSession, 则直接通过 HttpServletRequest 来进行获取
		else if (HttpSession.class.isAssignableFrom(paramType)) {
			HttpSession session = request.getSession();
			if (session != null && !paramType.isInstance(session)) {
				throw new IllegalStateException(
						"Current session is not of type [" + paramType.getName() + "]: " + session);
			}
			return session;
		}
		// 若请求参数是 InputStream, 则直接通过 HttpServletRequest 获取
		else if (InputStream.class.isAssignableFrom(paramType)) {
			InputStream inputStream = request.getInputStream();
			if (inputStream != null && !paramType.isInstance(inputStream)) {
				throw new IllegalStateException(
						"Request input stream is not of type [" + paramType.getName() + "]: " + inputStream);
			}
			return inputStream;
		}
		// 若请求参数是 Reader, 则直接通过 HttpServletRequest 获取
		else if (Reader.class.isAssignableFrom(paramType)) {
			Reader reader = request.getReader();
			if (reader != null && !paramType.isInstance(reader)) {
				throw new IllegalStateException(
						"Request body reader is not of type [" + paramType.getName() + "]: " + reader);
			}
			return reader;
		}
		// 若请求参数是 Reader, 则直接通过 HttpServletRequest 获取
		else if (Principal.class.isAssignableFrom(paramType)) {
			Principal userPrincipal = request.getUserPrincipal();
			if (userPrincipal != null && !paramType.isInstance(userPrincipal)) {
				throw new IllegalStateException(
						"Current user principal is not of type [" + paramType.getName() + "]: " + userPrincipal);
			}
			return userPrincipal;
		}
		// 若请求参数是 HttpMethod, 则直接通过 HttpServletRequest 获取
		else if (HttpMethod.class == paramType) {
			return HttpMethod.resolve(request.getMethod());
		}
		// 若请求参数是 Locale, 则直接通过 LocaleResolver 获取
		else if (Locale.class == paramType) {
			return RequestContextUtils.getLocale(request);
		}
		// 若请求参数是 TimeZone, 则直接通过 LocaleResolver 获取
		else if (TimeZone.class == paramType) {
			TimeZone timeZone = RequestContextUtils.getTimeZone(request);
			return (timeZone != null ? timeZone : TimeZone.getDefault());
		}
		// 若请求参数是 ZoneId, 则直接通过 LocaleResolver 获取
		else if ("java.time.ZoneId".equals(paramType.getName())) {
			return ZoneIdResolver.resolveZoneId(request);
		}
		else {
			// Should never happen...
			throw new UnsupportedOperationException(
					"Unknown parameter type [" + paramType.getName() + "] in " + parameter.getMethod());
		}
	}


	/**
	 * Inner class to avoid a hard-coded dependency on Java 8's {@link java.time.ZoneId}.
	 */
	@UsesJava8
	private static class ZoneIdResolver {

		public static Object resolveZoneId(HttpServletRequest request) {
			TimeZone timeZone = RequestContextUtils.getTimeZone(request);
			return (timeZone != null ? timeZone.toZoneId() : ZoneId.systemDefault());
		}
	}

}
