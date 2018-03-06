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

import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.multipart.support.RequestPartServletServerHttpRequest;

/**
 * Resolves the following method arguments:
 * <ul>
 * <li>Annotated with {@code @RequestPart}    被 @RequestPart 注解修饰的参数
 * <li>Of type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver} abstraction  类型是 MultipartFile 的参数
 * <li>Of type {@code javax.servlet.http.Part} in conjunction with Servlet 3.0 multipart requests        类型是 javax.servlet.http.Part 的参数
 * </ul>
 *
 * <p>When a parameter is annotated with {@code @RequestPart}, the content of the part is
 * passed through an {@link HttpMessageConverter} to resolve the method argument with the    <-- HttpMessageConverter 转化数据的策略通常依据 Content-Type
 * 'Content-Type' of the request part in mind. This is analogous(类似) to what @{@link RequestBody}
 * does to resolve an argument based on the content of a regular request.
 *
 * <p>When a parameter is not annotated or the name of the part is not specified,
 * it is derived from (来源于) the name of the method argument (方法注解的名字).
 *
 * <p>Automatic validation(校验) may be applied if the argument is annotated with  若被 @validation 注解修饰, 则将进行校验, 若校验失败, 则将报出 MethodArgumentNotValidException
 * {@code @javax.validation.Valid}. In case of validation failure, a {@link MethodArgumentNotValidException}
 * is raised and a 400 response status code returned if
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver} is configured.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 3.1
 */
public class RequestPartMethodArgumentResolver extends AbstractMessageConverterMethodArgumentResolver {

	/**
	 * Basic constructor with converters only. 依赖于 HttpMessageConverter 的构造器
	 */
	public RequestPartMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters) {
		super(messageConverters);
	}

	/**
	 * Constructor with converters and {@code Request~} and
	 * {@code ResponseBodyAdvice}.
	 */
	public RequestPartMethodArgumentResolver(List<HttpMessageConverter<?>> messageConverters,
			List<Object> requestResponseBodyAdvice) { // 依赖于 HttpMessageConverter & requestResponseBodyAdvice 的构造器

		super(messageConverters, requestResponseBodyAdvice);
	}


	/**
	 * Supports the following:
	 * <ul>
	 * <li>annotated with {@code @RequestPart}
	 * <li>of type {@link MultipartFile} unless annotated with {@code @RequestParam}
	 * <li>of type {@code javax.servlet.http.Part} unless annotated with {@code @RequestParam}
	 * </ul>
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (parameter.hasParameterAnnotation(RequestPart.class)) { // 若参数被 @RequestPart 修饰, 则直接返回 true
			return true;
		}
		else {
			if (parameter.hasParameterAnnotation(RequestParam.class)) { // 若参数被 @RequestParam 修饰, 则直接返回 false
				return false;
			}															// 判断参数是否是 MultipartFile | javax.servlet.http.Part
			return MultipartResolutionDelegate.isMultipartArgument(parameter.nestedIfOptional()); // nestedIfOptional : 若参数是 Optional, 则参数更深一层
		}
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest request, WebDataBinderFactory binderFactory) throws Exception {
		// 获取 HttpServletRequest
		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
		// 获取 注解 @RequestPart
		RequestPart requestPart = parameter.getParameterAnnotation(RequestPart.class);
		// 判断参数是否是必需的
		boolean isRequired = ((requestPart == null || requestPart.required()) && !parameter.isOptional());
		// 获取 @RequestPart 修饰参数的 name
		String name = getPartName(parameter, requestPart);
		// 若参数是 Optional, 则参数更深一层
		parameter = parameter.nestedIfOptional();
		Object arg = null;
		// 先解决 Multipart | Part 类型的参数
		Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
		if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) { // <-- 说明不是默认值
			arg = mpArg;
		}
		else {
			try {
				// 构建 RequestPartServletServerHttpRequest
				HttpInputMessage inputMessage = new RequestPartServletServerHttpRequest(servletRequest, name);
				// 通过 HttpMessageConverter 进行转化参数格式
				arg = readWithMessageConverters(inputMessage, parameter, parameter.getNestedGenericParameterType());
				// 创建 WebDataBinder
				WebDataBinder binder = binderFactory.createBinder(request, arg, name);
				if (arg != null) {
					// 进行参数的校验
					validateIfApplicable(binder, parameter);
					// 校验是否参数不合法
					if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
						throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
					}
				}
				mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());
			}
			catch (MissingServletRequestPartException ex) {
				if (isRequired) {
					throw ex;
				}
			}
			catch (MultipartException ex) {
				if (isRequired) {
					throw ex;
				}
			}
		}

		if (arg == null && isRequired) {
			if (!MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
				throw new MultipartException("Current request is not a multipart request");
			}
			else {
				throw new MissingServletRequestPartException(name);
			}
		}
		return adaptArgumentIfNecessary(arg, parameter);
	}
	// 获取参数的名字
	private String getPartName(MethodParameter methodParam, RequestPart requestPart) {
		String partName = (requestPart != null ? requestPart.name() : "");
		if (partName.isEmpty()) {
			partName = methodParam.getParameterName();
			if (partName == null) {
				throw new IllegalArgumentException("Request part name for argument type [" +
						methodParam.getNestedParameterType().getName() +
						"] not specified, and parameter name information not found in class file either.");
			}
		}
		return partName;
	}

}
