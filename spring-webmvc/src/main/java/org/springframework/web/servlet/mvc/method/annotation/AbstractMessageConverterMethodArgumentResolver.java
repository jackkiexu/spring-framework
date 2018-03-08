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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.UsesJava8;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/** 这个抽象类 相对于 HandlerMethodArgumentResolver 最重要的是 有了 HttpMessageConverter
 * A base class for resolving method argument values by reading from the body of
 * a request with {@link HttpMessageConverter}s.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
// 通过 HttpMessageConverter 消息转化器获取真实的请求参数
public abstract class AbstractMessageConverterMethodArgumentResolver implements HandlerMethodArgumentResolver {

	// 支持的 Http 请求的方法
	private static final Set<HttpMethod> SUPPORTED_METHODS = EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

	private static final Object NO_VALUE = new Object();


	protected final Log logger = LogFactory.getLog(getClass());
	// 解决 HandlerMethod 中的 argument 时使用到的 HttpMessageConverters
	protected final List<HttpMessageConverter<?>> messageConverters;
	// 支持的 MediaType  <-- 通过这里的 MediaType 来筛选对应的 HttpMessageConverter
	protected final List<MediaType> allSupportedMediaTypes;
	// Request/Response 的 Advice <- 这里的 Advice 其实就是 AOP 中 Advice 的概念
	// RequestAdvice 在从 request 中读取数据之前|后
	// ResponseAdvice 在 将数据写入 Response 之后
	private final RequestResponseBodyAdviceChain advice;


	/**
	 * Basic constructor with converters only.
	 */
	// 基于 HttpMessageConverter 的构造函数
	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters) {
		this(converters, null);
	}

	/**
	 * Constructor with converters and {@code Request~} and {@code ResponseBodyAdvice}.
	 * @since 4.2
	 */
	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters,
			List<Object> requestResponseBodyAdvice) {

		Assert.notEmpty(converters, "'messageConverters' must not be empty");
		this.messageConverters = converters;
		this.allSupportedMediaTypes = getAllSupportedMediaTypes(converters);
		this.advice = new RequestResponseBodyAdviceChain(requestResponseBodyAdvice);
	}


	/**
	 * Return the media types supported by all provided message converters sorted
	 * by specificity via {@link MediaType#sortBySpecificity(List)}.
	 */
	// 获取所有 HttpMessageConverter 所支持的 MediaType
	private static List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
		Set<MediaType> allSupportedMediaTypes = new LinkedHashSet<MediaType>();
		for (HttpMessageConverter<?> messageConverter : messageConverters) {
			allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
		}
		List<MediaType> result = new ArrayList<MediaType>(allSupportedMediaTypes);
		MediaType.sortBySpecificity(result);
		return Collections.unmodifiableList(result);
	}


	/**
	 * Return the configured {@link RequestBodyAdvice} and
	 * {@link RequestBodyAdvice} where each instance may be wrapped as a
	 * {@link org.springframework.web.method.ControllerAdviceBean ControllerAdviceBean}.
	 */
	// 获取 Request 读取数据的增强器 | 写入数据的增强器
	protected RequestResponseBodyAdviceChain getAdvice() {
		return this.advice;
	}

	/**
	 * Create the method argument value of the expected parameter type by
	 * reading from the given request.
	 * @param <T> the expected type of the argument value to be created
	 * @param webRequest the current request
	 * @param parameter the method parameter descriptor (may be {@code null})
	 * @param paramType the type of the argument value to be created
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest, MethodParameter parameter,
			Type paramType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {
		// 封装统一的入参对象 HttpInputMessage
		HttpInputMessage inputMessage = createInputMessage(webRequest);
		// 进行参数的解析
		return readWithMessageConverters(inputMessage, parameter, paramType);
	}

	/**
	 * Create the method argument value of the expected parameter type by reading
	 * from the given HttpInputMessage.
	 * @param <T> the expected type of the argument value to be created
	 * @param inputMessage the HTTP input message representing the current request
	 * @param parameter the method parameter descriptor (may be {@code null})
	 * @param targetType the target type, not necessarily the same as the method  <-- 参数的类型
	 * parameter type, e.g. for {@code HttpEntity<String>}.
	 * @return the created method argument value
	 * @throws IOException if the reading from the request fails
	 * @throws HttpMediaTypeNotSupportedException if no suitable message converter is found
	 */
	@SuppressWarnings("unchecked")
	protected <T> Object readWithMessageConverters(HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		MediaType contentType;
		boolean noContentType = false;
		try {
			contentType = inputMessage.getHeaders().getContentType(); 		// 获取 Http 请求头中的 contentType
		} catch (InvalidMediaTypeException ex) {
			throw new HttpMediaTypeNotSupportedException(ex.getMessage());  // 获取失败则报 HttpMediaTypeNotSupportedException, 根据 DefaultHandlerExceptionResolver, 则报出 Http.status = 415
		}
		if (contentType == null) {									  		// 若 contentType == null, 则设置默认值, application/octet-stream
			noContentType = true;
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}
		Class<?> contextClass = (parameter != null ? parameter.getContainingClass() : null);				// 获取 方法的声明类
		Class<T> targetClass = (targetType instanceof Class ? (Class<T>) targetType : null);				// 获取请求参数的类型
		if (targetClass == null) {																			// 若 targetClass 是 null, 则通过工具类 ResolvableType 进行解析
			ResolvableType resolvableType = (parameter != null ? ResolvableType.forMethodParameter(parameter) : ResolvableType.forType(targetType));
			targetClass = (Class<T>) resolvableType.resolve();												// 获取参数的类型
		}
		HttpMethod httpMethod = ((HttpRequest) inputMessage).getMethod();									// 获取请求的类型 HttpMethod (GET, POST, INPUT, DELETE 等)
		Object body = NO_VALUE;

		try {
			inputMessage = new EmptyBodyCheckingHttpInputMessage(inputMessage);
			for (HttpMessageConverter<?> converter : this.messageConverters) {								// 循环遍历 HttpMessageConverter, 找出支持的 HttpMessageConverter
				Class<HttpMessageConverter<?>> converterType = (Class<HttpMessageConverter<?>>) converter.getClass();
				// 下面分成两类 HttpMessageConverter 分别处理
				if (converter instanceof GenericHttpMessageConverter) {
					GenericHttpMessageConverter<?> genericConverter = (GenericHttpMessageConverter<?>) converter;
					// 判断 GenericHttpMessageConverter 是否支持 targetType + contextClass + contextType 这些类型
					if (genericConverter.canRead(targetType, contextClass, contentType)) {
						logger.info("Read [" + targetType + "] as \"" + contentType + "\" with [" + converter + "]");
						if (inputMessage.getBody() != null) { // 若处理后有 request 值
							// 在通过 GenericHttpMessageConverter 处理前 过一下 Request 的 Advice <-- 其实就是个切面
							inputMessage = getAdvice().beforeBodyRead(inputMessage, parameter, targetType, converterType);
							// 通过 GenericHttpMessageConverter 来处理请求的数据
							body = genericConverter.read(targetType, contextClass, inputMessage);
							// 在 GenericHttpMessageConverter 处理后在通过 Request 的 Advice 来做处理 <-- 其实就是个切面
							body = getAdvice().afterBodyRead(body, inputMessage, parameter, targetType, converterType);
						}
						else { // 若处理后没有值, 则通过 Advice 的 handleEmptyBody 方法来处理
							body = getAdvice().handleEmptyBody(null, inputMessage, parameter, targetType, converterType);
						}
						break;
					}
				}
				else if (targetClass != null) {
					// 判断 HttpMessageConverter 是否支持 这种类型的数据
					if (converter.canRead(targetClass, contentType)) {
						logger.info("Read [" + targetType + "] as \"" + contentType + "\" with [" + converter + "]");
						if (inputMessage.getBody() != null) { // 若处理后有 request 值
							// 在通过 HttpMessageConverter 处理前 过一下 Request 的 Advice <-- 其实就是个切面
							inputMessage = getAdvice().beforeBodyRead(inputMessage, parameter, targetType, converterType);
							// 通过 HttpMessageConverter 来处理请求的数据
							body = ((HttpMessageConverter<T>) converter).read(targetClass, inputMessage);
							// 在 HttpMessageConverter 处理后在通过 Request 的 Advice 来做处理 <-- 其实就是个切面
							body = getAdvice().afterBodyRead(body, inputMessage, parameter, targetType, converterType);
						}
						else { // 若 Http 请求的 body 是 空, 则直接通过 Request/ResponseAdvice 来进行处理
							body = getAdvice().handleEmptyBody(null, inputMessage, parameter, targetType, converterType);
						}
						break;
					}
				}
			}
		} catch (IOException ex) {
			throw new HttpMessageNotReadableException("I/O error while reading input message", ex);
		}

		if (body == NO_VALUE) {  // 若 body 里面没有数据, 则
			if (httpMethod == null || !SUPPORTED_METHODS.contains(httpMethod) || (noContentType && inputMessage.getBody() == null)) {
				return null;
			} // 不满足以上条件, 则报出异常
			throw new HttpMediaTypeNotSupportedException(contentType, this.allSupportedMediaTypes);
		}

		return body;
	}

	/**
	 * Create a new {@link HttpInputMessage} from the given {@link NativeWebRequest}.
	 * @param webRequest the web request to create an input message from
	 * @return the input message
	 */
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		return new ServletServerHttpRequest(servletRequest);
	}

	/**
	 * Validate the binding target if applicable.
	 * <p>The default implementation checks for {@code @javax.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter descriptor
	 * @since 4.1.5
	 * @see #isBindExceptionRequired
	 */
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) { // 校验是否可适用
		Annotation[] annotations = parameter.getParameterAnnotations();
		for (Annotation ann : annotations) {
			Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);   // 获取 ann 上的注解
			if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) { // 若 ann 上获取了注解了 @Validated, 或注解是以 Valid 开头
				Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));  // 获取 注解.value
				Object[] validationHints = (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
				binder.validate(validationHints);		// 对数据进行校验
				break;
			}
		}
	}

	/**
	 * Whether to raise a fatal bind exception on validation errors.
	 * @param binder the data binder used to perform data binding
	 * @param parameter the method parameter descriptor
	 * @return {@code true} if the next method argument is not of type {@link Errors}
	 * @since 4.1.5
	 */
	// 是否 抛出绑定数据错误的异常
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getMethod().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	/**
	 * Adapt the given argument against the method parameter, if necessary.
	 * @param arg the resolved argument
	 * @param parameter the method parameter descriptor
	 * @return the adapted argument, or the original resolved argument as-is
	 * @since 4.3.5
	 */
	// 针对 Optional 类型的参数的处理
	protected Object adaptArgumentIfNecessary(Object arg, MethodParameter parameter) {
		return (parameter.isOptional() ? OptionalResolver.resolveValue(arg) : arg);
	}


	private static class EmptyBodyCheckingHttpInputMessage implements HttpInputMessage {	// Http 请求的一个包装类

		private final HttpHeaders headers;

		private final InputStream body;

		private final HttpMethod method;


		public EmptyBodyCheckingHttpInputMessage(HttpInputMessage inputMessage) throws IOException {
			this.headers = inputMessage.getHeaders();
			InputStream inputStream = inputMessage.getBody();
			if (inputStream == null) {
				this.body = null;
			}
			else if (inputStream.markSupported()) {
				inputStream.mark(1);
				this.body = (inputStream.read() != -1 ? inputStream : null);
				inputStream.reset();
			}
			else {
				PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
				int b = pushbackInputStream.read();
				if (b == -1) {							// 读到 空的 body, 所以 body = null
					this.body = null;
				}
				else {
					this.body = pushbackInputStream;
					pushbackInputStream.unread(b);
				}
			}
			this.method = ((HttpRequest) inputMessage).getMethod();
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public InputStream getBody() throws IOException {
			return this.body;
		}

		public HttpMethod getMethod() {
			return this.method;
		}
	}


	/**
	 * Inner class to avoid hard-coded dependency on Java 8 Optional type...
	 */
	@UsesJava8
	private static class OptionalResolver {

		public static Object resolveValue(Object value) {
			if (value == null || (value instanceof Collection && ((Collection) value).isEmpty()) ||
					(value instanceof Object[] && ((Object[]) value).length == 0)) {
				return Optional.empty();
			}
			return Optional.of(value);
		}
	}

}
