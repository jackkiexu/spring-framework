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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * Resolves method arguments annotated with {@code @RequestBody} and handles return  // 解决被 @RequestBody 修饰的参数
 * values from methods annotated with {@code @ResponseBody} by reading and writing   // 解决被 @ResponseBody 修饰的返回值
 * to the body of the request or response with an {@link HttpMessageConverter}.
 *
 * <p>An {@code @RequestBody} method argument is also validated if it is annotated   // 若参数被 Valid 注解修饰, 则将会被校验器进行校验
 * with {@code @javax.validation.Valid}. In case of validation failure,				 // 若校验失败, 则异常 MethodArgumentNotValidException 抛出来
 * {@link MethodArgumentNotValidException} is raised and results in an HTTP 400
 * response status code if {@link DefaultHandlerExceptionResolver} is configured.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */

/** 主要功能
 * 1. 解决被 @RequestBody 注释的方法参数  <- 其间是用 HttpMessageConverter 进行参数的转换
 * 2. 解决被 @ResponseBody 注释的返回值  <- 其间是用 HttpMessageConverter 进行参数的转换
 */
public class RequestResponseBodyMethodProcessor extends AbstractMessageConverterMethodProcessor {

	/**
	 * Basic constructor with converters only. Suitable for resolving
	 * {@code @RequestBody}. For handling {@code @ResponseBody} consider also
	 * providing a {@code ContentNegotiationManager}.
	 */
	// 基于 HttpMessageConverter 的构造函数
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters) {
		super(converters);
	}

	/**
	 * Basic constructor with converters and {@code ContentNegotiationManager}.
	 * Suitable for resolving {@code @RequestBody} and handling
	 * {@code @ResponseBody} without {@code Request~} or
	 * {@code ResponseBodyAdvice}.
	 */
	// 基于 HttpMessageConverter 与 ContentNegotiationManager(PS: 作用 -> 根据请求 uri 尾缀, 或 Header 中的信息, 来决定 MediaType)
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager) {

		super(converters, manager);
	}

	/**
	 * Complete constructor for resolving {@code @RequestBody} method arguments.
	 * For handling {@code @ResponseBody} consider also providing a
	 * {@code ContentNegotiationManager}.
	 * @since 4.2
	 */
	// 根据 HttpMessageConverter 与 requestResponseBodyAdvice(request|response 增强器)
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			List<Object> requestResponseBodyAdvice) {

		super(converters, null, requestResponseBodyAdvice);
	}

	/**
	 * Complete constructor for resolving {@code @RequestBody} and handling
	 * {@code @ResponseBody}.
	 */
	// 根据 HttpMessageConverter, ContentNegotiationManager(PS: 作用 -> 根据请求 uri 尾缀, 或 Header 中的信息, 来决定 MediaType), requestResponseBodyAdvice(读数据|写数据的增强器) 的构造函数
	public RequestResponseBodyMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager, List<Object> requestResponseBodyAdvice) {

		super(converters, manager, requestResponseBodyAdvice);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {	// 若参数被 @RequestBody 注释则支持, 则对参数进行解析
		return parameter.hasParameterAnnotation(RequestBody.class);
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {					// Handler 类上是否被 @ResponseBody 注解 || 返回类型上是否被 @ResponseBody 修饰
		boolean first = AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), ResponseBody.class); //
		boolean sesond = returnType.hasMethodAnnotation(ResponseBody.class);
		return (AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), ResponseBody.class) ||
				returnType.hasMethodAnnotation(ResponseBody.class));
	}

	/**
	 * Throws MethodArgumentNotValidException if validation fails.
	 * @throws HttpMessageNotReadableException if {@link RequestBody#required()}
	 * is {@code true} and there is no body content or if there is no suitable
	 * converter to read the content with.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		parameter = parameter.nestedIfOptional();	// 获取嵌套参数 <- 有可能参数是用 Optional
		// 通过 HttpMessageConverter 来将数据转换成合适的类型
		Object arg = readWithMessageConverters(webRequest, parameter, parameter.getNestedGenericParameterType());
		// 获取参数的名字
		String name = Conventions.getVariableNameForParameter(parameter);
		// 构建 WebDataBinder
		WebDataBinder binder = binderFactory.createBinder(webRequest, arg, name);  // 参数中的第二个值 arg 其实就是 DataBinder 的 target
		if (arg != null) {
			// @Validated 进行参数的校验
			validateIfApplicable(binder, parameter);
			if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) { // 若有异常则直接暴出来
				throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
			}
		}
		mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());
		// 对 Optional 的处理
		return adaptArgumentIfNecessary(arg, parameter);
	}

	@Override
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest, MethodParameter parameter,
			Type paramType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {
		// 从 NativeWebRequest 中获取  HttpServletRequest
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		// 封装 ServletServerHttpRequest
		ServletServerHttpRequest inputMessage = new ServletServerHttpRequest(servletRequest);
		// 通过 InputMessage 中读取参数的内容, 并且 通过 HttpMessageConverter 来将数据转换成 paramType 类型的参数
		Object arg = readWithMessageConverters(inputMessage, parameter, paramType);
		if (arg == null) {
			if (checkRequired(parameter)) { // 检测参数是否是必需的
				throw new HttpMessageNotReadableException("Required request body is missing: " + parameter.getMethod().toGenericString());
			}
		}
		return arg; // 返回参数值
	}

	protected boolean checkRequired(MethodParameter parameter) { // 检查是否是必需
		return (parameter.getParameterAnnotation(RequestBody.class).required() && !parameter.isOptional());
	}

	// 处理返回值
	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		mavContainer.setRequestHandled(true); // 标志请求被处理过了, 则 视图解析就不需要了
		// 创建 ServletServerHttpRequest
		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		// 创建 ServletServerHttpResponse
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		// 将数据转换成指定的格式, 写到远端
		// Try even with null return value. ResponseBodyAdvice could get involved.
		writeWithMessageConverters(returnValue, returnType, inputMessage, outputMessage);
	}

}
