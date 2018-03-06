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

package org.springframework.web.method.annotation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link Map} method arguments annotated with an @{@link RequestParam}
 * where the annotation does not specify a request parameter name.
 * See {@link RequestParamMethodArgumentResolver} for resolving {@link Map}
 * method arguments with a request parameter name.
 *
 * <p>The created {@link Map} contains all request parameter name/value pairs.
 * If the method parameter type is {@link MultiValueMap} instead, the created
 * map contains all request parameters and all there values for cases where
 * request parameters have multiple values.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 * @see RequestParamMethodArgumentResolver
 */
// 从 HttpServletRequest 里面获取所有请求参数, 最后封装成 LinkedHashMap|LinkedMultiValueMap 的参数解析器
public class RequestParamMapMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 获取参数上面的 @RequestParam
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null) {
			if (Map.class.isAssignableFrom(parameter.getParameterType())) {
				return !StringUtils.hasText(requestParam.name()); // 若参数是 Map 类型, 且 @RequestParam 中没有指定 name, 则返回 true
			}
		}
		return false;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		// 获取参数的类型
		Class<?> paramType = parameter.getParameterType();
		// 获取 HttpServletRequest 的 parameterMap
		Map<String, String[]> parameterMap = webRequest.getParameterMap();
		if (MultiValueMap.class.isAssignableFrom(paramType)) { // 若参数类型是 MultiValueMap
			MultiValueMap<String, String> result = new LinkedMultiValueMap<String, String>(parameterMap.size());
			for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) { // 将 parameterMap 中的数据装载到 MultiValueMap 中
				for (String value : entry.getValue()) {
					result.add(entry.getKey(), value);
				}
			}
			return result;
		}
		else {
			Map<String, String> result = new LinkedHashMap<String, String>(parameterMap.size()); // 将 parameterMap 所有数据都装载到 result 里面
			for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
				if (entry.getValue().length > 0) {
					result.put(entry.getKey(), entry.getValue()[0]);
				}
			}
			return result;
		}
	}
}
