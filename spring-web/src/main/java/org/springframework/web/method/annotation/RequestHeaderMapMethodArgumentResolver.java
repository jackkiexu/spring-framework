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

package org.springframework.web.method.annotation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link Map} method arguments annotated with {@code @RequestHeader}.
 * For individual header values annotated with {@code @RequestHeader} see
 * {@link RequestHeaderMethodArgumentResolver} instead.
 *
 * <p>The created {@link Map} contains all request header name/value pairs.
 * The method parameter type may be a {@link MultiValueMap} to receive all
 * values for a header, not only the first one.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
// 解决被 @RequestHeader 注解修饰, 并且类型是 Map 的参数, HandlerMethodArgumentResolver会将 Http header 中的所有 name <--> value 都放入其中
public class RequestHeaderMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {						// 参数被 RequestHeader 注释, 且参数是 Map
		return (parameter.hasParameterAnnotation(RequestHeader.class) &&
				Map.class.isAssignableFrom(parameter.getParameterType()));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		// 获取参数的类型
		Class<?> paramType = parameter.getParameterType();
		// 若参数类型是 MultiValueMap
		if (MultiValueMap.class.isAssignableFrom(paramType)) {
			MultiValueMap<String, String> result;
			// 若参数类型是 HttpHeaders, 则直接构造一个 HttpHeaders
			if (HttpHeaders.class.isAssignableFrom(paramType)) {
				result = new HttpHeaders();
			}
			else { // 构造 LinkedMultiValueMap
				result = new LinkedMultiValueMap<String, String>();
			}
			// 遍历 所有 Header 中的 names
			for (Iterator<String> iterator = webRequest.getHeaderNames(); iterator.hasNext();) {
				// 获取 headerName
				String headerName = iterator.next();
				// 获取 headerName 对应的 value
				String[] headerValues = webRequest.getHeaderValues(headerName);
				if (headerValues != null) {
					// 将 headerName <--> headerValue 加入 result
					for (String headerValue : headerValues) {
						result.add(headerName, headerValue);
					}
				}
			}
			return result;
		}
		else {
			// 遍历 headerNames 并将 headerName <--> headerValue 加入 result
			Map<String, String> result = new LinkedHashMap<String, String>();
			for (Iterator<String> iterator = webRequest.getHeaderNames(); iterator.hasNext();) {
				String headerName = iterator.next();
				String headerValue = webRequest.getHeader(headerName);
				if (headerValue != null) {
					result.put(headerName, headerValue);
				}
			}
			return result;
		}
	}

}
