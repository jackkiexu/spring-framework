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

package org.springframework.web.servlet.mvc.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.Source;

import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.ui.Model;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Implementation of the {@link org.springframework.web.servlet.HandlerExceptionResolver} interface that handles
 * exceptions through the {@link ExceptionHandler} annotation.
 *
 * <p>This exception resolver is enabled by default in the {@link org.springframework.web.servlet.DispatcherServlet}.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 * @deprecated as of Spring 3.2, in favor of
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver ExceptionHandlerExceptionResolver}
 */
@Deprecated
public class AnnotationMethodHandlerExceptionResolver extends AbstractHandlerExceptionResolver {

	/**
	 * Arbitrary {@link Method} reference, indicating no method found in the cache.
	 */
	private static final Method NO_METHOD_FOUND = ClassUtils.getMethodIfAvailable(System.class, "currentTimeMillis");


	private final Map<Class<?>, Map<Class<? extends Throwable>, Method>> exceptionHandlerCache =
			new ConcurrentHashMap<Class<?>, Map<Class<? extends Throwable>, Method>>(64);

	private WebArgumentResolver[] customArgumentResolvers;

	private HttpMessageConverter<?>[] messageConverters =
			new HttpMessageConverter<?>[] {new ByteArrayHttpMessageConverter(), new StringHttpMessageConverter(),
			new SourceHttpMessageConverter<Source>(),
			new org.springframework.http.converter.xml.XmlAwareFormHttpMessageConverter()};


	/**
	 * Set a custom ArgumentResolvers to use for special method parameter types.
	 * <p>Such a custom ArgumentResolver will kick in first, having a chance to resolve
	 * an argument value before the standard argument handling kicks in.
	 */
	public void setCustomArgumentResolver(WebArgumentResolver argumentResolver) {
		this.customArgumentResolvers = new WebArgumentResolver[]{argumentResolver};
	}

	/**
	 * Set one or more custom ArgumentResolvers to use for special method parameter types.
	 * <p>Any such custom ArgumentResolver will kick in first, having a chance to resolve
	 * an argument value before the standard argument handling kicks in.
	 */
	public void setCustomArgumentResolvers(WebArgumentResolver[] argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(HttpMessageConverter<?>[] messageConverters) {
		this.messageConverters = messageConverters;
	}


	@Override
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

		if (handler != null) { // 获取对应的激活方法
			Method handlerMethod = findBestExceptionHandlerMethod(handler, ex);	// 获取处理这个异常的最符合的方法
			if (handlerMethod != null) { // 封装对应的请求参数
				ServletWebRequest webRequest = new ServletWebRequest(request, response);
				try {	//  resolve 方法对应的参数的真实数据
					Object[] args = resolveHandlerArguments(handlerMethod, handler, webRequest, ex);
					if (logger.isDebugEnabled()) {
						logger.debug("Invoking request handler method: " + handlerMethod);
					}
					Object retVal = doInvokeMethod(handlerMethod, handler, args);
					return getModelAndView(handlerMethod, retVal, webRequest);
				}
				catch (Exception invocationEx) {
					logger.error("Invoking request method resulted in exception : " + handlerMethod, invocationEx);
				}
			}
		}
		return null;
	}

	/**
	 * Finds the handler method that matches the thrown exception best.
	 * @param handler the handler object
	 * @param thrownException the exception to be handled
	 * @return the best matching method; or {@code null} if none is found
	 */
	private Method findBestExceptionHandlerMethod(Object handler, final Exception thrownException) {
		final Class<?> handlerType = ClassUtils.getUserClass(handler); // 获取真实的类 <-- 这里真实是针对 CGLIB 生成的类
		final Class<? extends Throwable> thrownExceptionType = thrownException.getClass();
		Method handlerMethod = null;
		// exceptionHandlerCache 中 key 是 异常处理类, value 又是一个 Map<异常的类型, 对应需要激活的方法 HandlerMethod>
		Map<Class<? extends Throwable>, Method> handlers = this.exceptionHandlerCache.get(handlerType);
		if (handlers != null) {
			handlerMethod = handlers.get(thrownExceptionType);	// 通过异常获取对应的 HandlerMethod
			if (handlerMethod != null) {
				return (handlerMethod == NO_METHOD_FOUND ? null : handlerMethod);
			}
		}
		else {	// 若 handlers == null, 则创建一个 Map<Class<? extends Throwable>, Method>
			handlers = new ConcurrentHashMap<Class<? extends Throwable>, Method>(16);
			this.exceptionHandlerCache.put(handlerType, handlers);
		}

		final Map<Class<? extends Throwable>, Method> matchedHandlers = new HashMap<Class<? extends Throwable>, Method>();
		// 针对异常的解析的操作
		ReflectionUtils.doWithMethods(handlerType, new ReflectionUtils.MethodCallback() {
			@Override
			public void doWith(Method method) {
				method = ClassUtils.getMostSpecificMethod(method, handlerType);  // 获取真实的方法
				List<Class<? extends Throwable>> handledExceptions = getHandledExceptions(method);  // 返回 method 上 @ExceptionHandler 注解中配置的异常的类型
				for (Class<? extends Throwable> handledException : handledExceptions) { // handledExceptions 中存储的是 @ExceptionHandler 注解上标示的异常的类型
					if (handledException.isAssignableFrom(thrownExceptionType)) {	// 判断传来的异常 thrownExceptionType是否是 handledException 的子类
						if (!matchedHandlers.containsKey(handledException)) {
							matchedHandlers.put(handledException, method);
						}
						else {
							Method oldMappedMethod = matchedHandlers.get(handledException);
							if (!oldMappedMethod.equals(method)) { // 若与老的 Method 不相等, 则直接报出异常
								throw new IllegalStateException(
										"Ambiguous exception handler mapped for " + handledException + "]: {" +
												oldMappedMethod + ", " + method + "}.");
							}
						}
					}
				}
			}
		});
		// 当遇到多个 method 时, 通过排序获取最贴近的 方法
		handlerMethod = getBestMatchingMethod(matchedHandlers, thrownException);
		handlers.put(thrownExceptionType, (handlerMethod == null ? NO_METHOD_FOUND : handlerMethod)); // 设置到 handlers 中 <- handlers 已经在缓存中
		return handlerMethod;
	}

	/**
	 * Returns all the exception classes handled by the given method.
	 * <p>The default implementation looks for exceptions in the annotation,
	 * or - if that annotation element is empty - any exceptions listed in the method parameters if the method
	 * is annotated with {@code @ExceptionHandler}.
	 * @param method the method
	 * @return the handled exceptions
	 */// 返回 注解 @ExceptionHandler 中的 异常类型
	@SuppressWarnings("unchecked")
	protected List<Class<? extends Throwable>> getHandledExceptions(Method method) {
		List<Class<? extends Throwable>> result = new ArrayList<Class<? extends Throwable>>();
		ExceptionHandler exceptionHandler = AnnotationUtils.findAnnotation(method, ExceptionHandler.class);
		if (exceptionHandler != null) {
			if (!ObjectUtils.isEmpty(exceptionHandler.value())) {
				result.addAll(Arrays.asList(exceptionHandler.value()));	// 获取 @ExceptionHandler 注解中指定的 异常的类型
			}
			else { // 若 @ExceptionHandler 中没有设置
				for (Class<?> param : method.getParameterTypes()) {
					if (Throwable.class.isAssignableFrom(param)) {
						result.add((Class<? extends Throwable>) param);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Uses the {@link ExceptionDepthComparator} to find the best matching method.
	 * @return the best matching method, or {@code null} if none found
	 */
	private Method getBestMatchingMethod( // 当遇到多个 Throwable, 则选择最贴近的 resolveMethod
			Map<Class<? extends Throwable>, Method> resolverMethods, Exception thrownException) {

		if (resolverMethods.isEmpty()) {
			return null;
		}
		Class<? extends Throwable> closestMatch =
				ExceptionDepthComparator.findClosestMatch(resolverMethods.keySet(), thrownException);
		Method method = resolverMethods.get(closestMatch);
		return ((method == null) || (NO_METHOD_FOUND == method)) ? null : method;
	}

	/**
	 * Resolves the arguments for the given method. Delegates to {@link #resolveCommonArgument}.
	 */
	private Object[] resolveHandlerArguments(Method handlerMethod, Object handler,
			NativeWebRequest webRequest, Exception thrownException) throws Exception {

		Class<?>[] paramTypes = handlerMethod.getParameterTypes(); // 方法的参数的类型
		Object[] args = new Object[paramTypes.length];			   // 存储方法值的数组
		Class<?> handlerType = handler.getClass();				   // handlerMethod 所属于的 Class
		for (int i = 0; i < args.length; i++) {
			MethodParameter methodParam = new SynthesizingMethodParameter(handlerMethod, i);
			GenericTypeResolver.resolveParameterType(methodParam, handlerType);
			Class<?> paramType = methodParam.getParameterType();	// 参数的类型
			Object argValue = resolveCommonArgument(methodParam, webRequest, thrownException);  // 进行参数的解析, 其实主要还是通过 HttpServletRequest, HttpServletResponse 里面获取de
			if (argValue != WebArgumentResolver.UNRESOLVED) {
				args[i] = argValue;
			}
			else {
				throw new IllegalStateException("Unsupported argument [" + paramType.getName() +
						"] for @ExceptionHandler method: " + handlerMethod);
			}
		}
		return args;
	}

	/**
	 * Resolves common method arguments. Delegates to registered {@link #setCustomArgumentResolver(WebArgumentResolver)
	 * argumentResolvers} first, then checking {@link #resolveStandardArgument}.
	 * @param methodParameter the method parameter
	 * @param webRequest the request
	 * @param thrownException the exception thrown
	 * @return the argument value, or {@link WebArgumentResolver#UNRESOLVED}
	 */
	protected Object resolveCommonArgument(MethodParameter methodParameter, NativeWebRequest webRequest,
			Exception thrownException) throws Exception {

		// Invoke custom argument resolvers if present...
		if (this.customArgumentResolvers != null) {
			for (WebArgumentResolver argumentResolver : this.customArgumentResolvers) {
				Object value = argumentResolver.resolveArgument(methodParameter, webRequest);
				if (value != WebArgumentResolver.UNRESOLVED) {
					return value;
				}
			}
		}

		// Resolution of standard parameter types...
		Class<?> paramType = methodParameter.getParameterType(); // 获取方法的参数类型
		Object value = resolveStandardArgument(paramType, webRequest, thrownException);
		if (value != WebArgumentResolver.UNRESOLVED && !ClassUtils.isAssignableValue(paramType, value)) {
			throw new IllegalStateException(
					"Standard argument type [" + paramType.getName() + "] resolved to incompatible value of type [" +
							(value != null ? value.getClass() : null) +
							"]. Consider declaring the argument type in a less specific fashion.");
		}
		return value;
	}

	/**
	 * Resolves standard method arguments. The default implementation handles {@link NativeWebRequest},
	 * {@link ServletRequest}, {@link ServletResponse}, {@link HttpSession}, {@link Principal},
	 * {@link Locale}, request {@link InputStream}, request {@link Reader}, response {@link OutputStream},
	 * response {@link Writer}, and the given {@code thrownException}.
	 * @param parameterType the method parameter type
	 * @param webRequest the request
	 * @param thrownException the exception thrown
	 * @return the argument value, or {@link WebArgumentResolver#UNRESOLVED}
	 */ // 解析方法的参数
	protected Object resolveStandardArgument(Class<?> parameterType, NativeWebRequest webRequest,
			Exception thrownException) throws Exception {

		if (parameterType.isInstance(thrownException)) {				// 若是异常的类型, 则直接返回
			return thrownException;
		}
		else if (WebRequest.class.isAssignableFrom(parameterType)) {	// 若是WebRequest的类型, 则直接返回
			return webRequest;
		}

		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

		if (ServletRequest.class.isAssignableFrom(parameterType)) {	    // 若是ServletRequest的类型, 则直接返回 HttpServletRequest
			return request;
		}
		else if (ServletResponse.class.isAssignableFrom(parameterType)) { // 下面也是通过 参数的类型从 NativeWebRequest 获取对应的数据
			return response;
		}
		else if (HttpSession.class.isAssignableFrom(parameterType)) {
			return request.getSession();
		}
		else if (Principal.class.isAssignableFrom(parameterType)) {
			return request.getUserPrincipal();
		}
		else if (Locale.class == parameterType) {
			return RequestContextUtils.getLocale(request);
		}
		else if (InputStream.class.isAssignableFrom(parameterType)) {
			return request.getInputStream();
		}
		else if (Reader.class.isAssignableFrom(parameterType)) {
			return request.getReader();
		}
		else if (OutputStream.class.isAssignableFrom(parameterType)) {
			return response.getOutputStream();
		}
		else if (Writer.class.isAssignableFrom(parameterType)) {
			return response.getWriter();
		}
		else {
			return WebArgumentResolver.UNRESOLVED;

		}
	}

	private Object doInvokeMethod(Method method, Object target, Object[] args) throws Exception {
		ReflectionUtils.makeAccessible(method);
		try {
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			ReflectionUtils.rethrowException(ex.getTargetException());
		}
		throw new IllegalStateException("Should never get here");
	}

	@SuppressWarnings("unchecked")
	private ModelAndView getModelAndView(Method handlerMethod, Object returnValue, ServletWebRequest webRequest)
			throws Exception {
		// 从处理异常的方法上获取 @ResponseStatus 注解的信息
		ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(handlerMethod, ResponseStatus.class);
		if (responseStatus != null) {
			HttpStatus statusCode = responseStatus.code();	// 获取 @ResponseStatus 中设置的 Http Code
			String reason = responseStatus.reason();		// 获取 @ResponseStatus 中设置的 reason
			if (!StringUtils.hasText(reason)) {
				webRequest.getResponse().setStatus(statusCode.value());  // 设置 Http Code
			}
			else {
				webRequest.getResponse().sendError(statusCode.value(), reason); // 设置对应的 reason
			}
		}

		if (returnValue != null && AnnotationUtils.findAnnotation(handlerMethod, ResponseBody.class) != null) {
			return handleResponseBody(returnValue, webRequest);
		}

		if (returnValue instanceof ModelAndView) {
			return (ModelAndView) returnValue;
		}
		else if (returnValue instanceof Model) {
			return new ModelAndView().addAllObjects(((Model) returnValue).asMap());
		}
		else if (returnValue instanceof Map) {
			return new ModelAndView().addAllObjects((Map<String, Object>) returnValue);
		}
		else if (returnValue instanceof View) {
			return new ModelAndView((View) returnValue);
		}
		else if (returnValue instanceof String) {
			return new ModelAndView((String) returnValue);
		}
		else if (returnValue == null) {
			return new ModelAndView();
		}
		else {
			throw new IllegalArgumentException("Invalid handler method return value: " + returnValue);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes", "resource" })
	private ModelAndView handleResponseBody(Object returnValue, ServletWebRequest webRequest)
			throws ServletException, IOException {

		HttpInputMessage inputMessage = new ServletServerHttpRequest(webRequest.getRequest());
		List<MediaType> acceptedMediaTypes = inputMessage.getHeaders().getAccept();
		if (acceptedMediaTypes.isEmpty()) {
			acceptedMediaTypes = Collections.singletonList(MediaType.ALL);
		}
		MediaType.sortByQualityValue(acceptedMediaTypes);
		HttpOutputMessage outputMessage = new ServletServerHttpResponse(webRequest.getResponse());
		Class<?> returnValueType = returnValue.getClass();
		if (this.messageConverters != null) {
			for (MediaType acceptedMediaType : acceptedMediaTypes) {
				for (HttpMessageConverter messageConverter : this.messageConverters) {
					if (messageConverter.canWrite(returnValueType, acceptedMediaType)) {
						messageConverter.write(returnValue, acceptedMediaType, outputMessage);
						return new ModelAndView();
					}
				}
			}
		}
		if (logger.isWarnEnabled()) {
			logger.warn("Could not find HttpMessageConverter that supports return type [" + returnValueType + "] and " +
					acceptedMediaTypes);
		}
		return null;
	}

}
