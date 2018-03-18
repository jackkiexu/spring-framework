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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.ui.ModelMap;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively to re-configure all argument and return value types use
 * {@link #setArgumentResolvers} and {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver
		implements ApplicationContextAware, InitializingBean {
	// HandlerMethod 参数解析器
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;
	// 组合模式的 HandlerMethod 参数解析器
	private HandlerMethodArgumentResolverComposite argumentResolvers;
	// HandlerMethod 返回值处理器
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;
	// 组合模式的 HandlerMethod 返回值处理器
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
	// Http 消息转换器
	private List<HttpMessageConverter<?>> messageConverters;
	// MediaType 解决器(PS: 根据请求 uri 尾缀, 或 Header 中的信息, 来决定 MediaType)
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();
	// 对 Response 进行增强的 Advice
	private final List<Object> responseBodyAdvice = new ArrayList<Object>();

	private ApplicationContext applicationContext;

	private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache = new ConcurrentHashMap<Class<?>, ExceptionHandlerMethodResolver>(64);
	// 被 @ControllerAdvice 注解修饰的的处理类, 再被包装成 ExceptionHandlerMethodResolver
	private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache =
			new LinkedHashMap<ControllerAdviceBean, ExceptionHandlerMethodResolver>();


	public ExceptionHandlerExceptionResolver() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316
		// 设置常见的 HttpMessageConverter
		this.messageConverters = new ArrayList<HttpMessageConverter<?>>();
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<Source>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers= argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
		return this.argumentResolvers;
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
		return this.returnValueHandlers;
	}

	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Return the configured {@link ContentNegotiationManager}.
	 */
	public ContentNegotiationManager getContentNegotiationManager() {
		return this.contentNegotiationManager;
	}

	/**
	 * Add one or more components to be invoked after the execution of a controller
	 * method annotated with {@code @ResponseBody} or returning {@code ResponseEntity}
	 * but before the body is written to the response with the selected
	 * {@code HttpMessageConverter}.
	 */
	public void setResponseBodyAdvice(List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		this.responseBodyAdvice.clear();
		if (responseBodyAdvice != null) {
			this.responseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}


	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBodyAdvice beans
		initExceptionHandlerAdviceCache();

		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers(); // 获取 HttpMessageConverter
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers); // 通过组合模式将属性转换器组合起来
		}
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers(); // 获取 HandlerMethodReturnValueHandler
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers); // 通过组合模式将 返回值处理器 组合起来
		}
	}

	private void initExceptionHandlerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for exception mappings: " + getApplicationContext());
		}
		// 收集 ApplicationContext 中所有被 @ControllerAdvice 注解修饰的 Bean, 并封装成 ControllerAdviceBean
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		AnnotationAwareOrderComparator.sort(adviceBeans); // 对 ControllerAdviceBean 进行排序

		for (ControllerAdviceBean adviceBean : adviceBeans) { // 将 ControllerAdviceBean.bean.getBeanType 转换成 ExceptionHandlerMethodResolver
			ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(adviceBean.getBeanType());
			if (resolver.hasExceptionMappings()) {
				this.exceptionHandlerAdviceCache.put(adviceBean, resolver);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ExceptionHandler methods in " + adviceBean);
				}
			}
			if (ResponseBodyAdvice.class.isAssignableFrom(adviceBean.getBeanType())) {
				this.responseBodyAdvice.add(adviceBean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected ResponseBodyAdvice implementation in " + adviceBean);
				}
			}
		}
	}

	/**
	 * Return an unmodifiable Map with the {@link ControllerAdvice @ControllerAdvice}
	 * beans discovered in the ApplicationContext. The returned map will be empty if
	 * the method is invoked before the bean has been initialized via
	 * {@link #afterPropertiesSet()}.
	 */
	public Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> getExceptionHandlerAdviceCache() {
		return Collections.unmodifiableMap(this.exceptionHandlerAdviceCache);
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() { // 获取参数解析器
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();

		// Annotation-based argument resolution 基于注解的参数 resolve
		resolvers.add(new SessionAttributeMethodArgumentResolver());   // 针对 被 @SessionAttribute 修饰的参数起作用, 参数的获取一般通过 HttpServletRequest.getAttribute(name, RequestAttributes.SCOPE_SESSION)
		resolvers.add(new RequestAttributeMethodArgumentResolver());   // 针对 被 @RequestAttribute 修饰的参数起作用, 参数的获取一般通过 HttpServletRequest.getAttribute(name, RequestAttributes.SCOPE_REQUEST)

		// Type-based argument resolution 基于类型的参数解决
		resolvers.add(new ServletRequestMethodArgumentResolver());	   // 针对 一些基础类的参数解决, 参数的获取一般通过 HttpServletRequest
		resolvers.add(new ServletResponseMethodArgumentResolver());    // 针对 一些基础类的参数解决, 参数的获取一般通过 HttpServletResponse
		resolvers.add(new RedirectAttributesMethodArgumentResolver()); // 针对 RedirectAttributes及其子类的参数
		resolvers.add(new ModelMethodProcessor());					   // 针对 Model及其子类的参数, 数据的获取一般通过 ModelAndViewContainer.getModel()

		// Custom arguments  获取一些自定义的 HttpMessageConverter
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<HandlerMethodReturnValueHandler>();

		// Single-purpose return value types 具有目的性的返回值处理
		handlers.add(new ModelAndViewMethodReturnValueHandler());   // 针对 ModelAndView 及其子类的返回值处理器, 主要还是将 ModelAndView 中的 status, model 设置到 ModelAndViewContainer
		handlers.add(new ModelMethodProcessor());					// 针对 Model 及其子类的返回值处理器, 主要还是将 ModelAndView 中的 model 设置到 ModelAndViewContainer  <-- 既是返回值处理器, 也是参数解析器
		handlers.add(new ViewMethodReturnValueHandler());			// 针对 View 及其子类的返回值处理器, 主要还是将 View 设置到 ModelAndViewContainer
		handlers.add(new HttpEntityMethodProcessor(					// 针对 HttpEntity 及其子类的返回值处理器, 主要还是将 HttpEntity 中的信息写入到 远端
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		// Annotation-based return value types 基于注解的返回值处理
		handlers.add(new ModelAttributeMethodProcessor(false));     // 解析被注解 @ModelAttribute 修饰, 且类型是 Map 的参数, 数据的获取通过 ModelAndViewContainer 获取, 通过 DataBinder 进行绑定
		handlers.add(new RequestResponseBodyMethodProcessor(		// 解析被注解 @RequestBody 修饰的参数, 以及被@ResponseBody修饰的返回值, 数据的获取通过 HttpServletRequest 获取, 根据 MediaType通过HttpMessageConverter转换成对应的格式, 在处理返回值时 也是通过 MediaType 选择合适HttpMessageConverter, 进行转换格式, 并输出
				getMessageConverters(), this.contentNegotiationManager, this.responseBodyAdvice));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());		// 支持返回值为 CharSequence 类型, 设置 ModelAndViewContainer.setViewName
		handlers.add(new MapMethodProcessor());						// 支持返回值为 Map, 并将结果设置到 ModelAndViewContainer

		// Custom return value types	// 获取自定义 返回值处理器
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		handlers.add(new ModelAttributeMethodProcessor(true));

		return handlers;
	}


	/**
	 * Find an {@code @ExceptionHandler} method and invoke it to handle the raised exception.
	 */
	@Override
	protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod, Exception exception) {

		ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
		if (exceptionHandlerMethod == null) {
			return null;
		}

		exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		ModelAndViewContainer mavContainer = new ModelAndViewContainer();

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Invoking @ExceptionHandler method: " + exceptionHandlerMethod);
			}
			Throwable cause = exception.getCause();
			if (cause != null) {
				// Expose cause as provided argument as well // 激活处理异常的方法
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, cause, handlerMethod);
			}
			else {
				// Otherwise, just the given exception as-is
				exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, exception, handlerMethod);
			}
		}
		catch (Throwable invocationEx) {
			// Any other than the original exception is unintended here,
			// probably an accident (e.g. failed assertion or the like).
			if (invocationEx != exception && logger.isWarnEnabled()) {
				logger.warn("Failed to invoke @ExceptionHandler method: " + exceptionHandlerMethod, invocationEx);
			}
			// Continue with default processing of the original exception...
			return null;
		}

		if (mavContainer.isRequestHandled()) { // mavContainer.isRequestHandled() == true 表示请求已经被处理了
			return new ModelAndView();
		}
		else {
			ModelMap model = mavContainer.getModel();
			HttpStatus status = mavContainer.getStatus();
			ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, status);
			mav.setViewName(mavContainer.getViewName());
			if (!mavContainer.isViewReference()) {
				mav.setView((View) mavContainer.getView());
			}
			if (model instanceof RedirectAttributes) {
				Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
				request = webRequest.getNativeRequest(HttpServletRequest.class);
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
			return mav;
		}
	}

	/**
	 * Find an {@code @ExceptionHandler} method for the given exception. The default
	 * implementation searches methods in the class hierarchy of the controller first
	 * and if not found, it continues searching for additional {@code @ExceptionHandler}
	 * methods assuming some {@linkplain ControllerAdvice @ControllerAdvice}
	 * Spring-managed beans were detected.
	 * @param handlerMethod the method where the exception was raised (may be {@code null})
	 * @param exception the raised exception
	 * @return a method to handle the exception, or {@code null}
	 */
	protected ServletInvocableHandlerMethod getExceptionHandlerMethod(HandlerMethod handlerMethod, Exception exception) {
		Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);

		if (handlerMethod != null) {
			ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType); // 先从缓存中获取 handlerType 对应的 ExceptionHandlerMethodResolver
			if (resolver == null) {
				resolver = new ExceptionHandlerMethodResolver(handlerType);		// 封装 ExceptionHandlerMethodResolver <-- 这里已经提取出 每个method处理的异常类型
				this.exceptionHandlerCache.put(handlerType, resolver);
			}
			Method method = resolver.resolveMethod(exception);		// 获取能处理异常 exception 的 Method
			if (method != null) {
				return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method);  // 返回 针对这个 异常的 InvocableHandlerMethod
			}
		}
		// 若上面的 handlerType 中没有能解析这个 Exception 的method, 则 再通过 exceptionHandlerAdviceCache 进行查找
		for (Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache.entrySet()) {
			if (entry.getKey().isApplicableToBeanType(handlerType)) {
				ExceptionHandlerMethodResolver resolver = entry.getValue();
				Method method = resolver.resolveMethod(exception);
				if (method != null) {	// 封装 InvocableHandlerMethod -> 用于处理异常
					return new ServletInvocableHandlerMethod(entry.getKey().resolveBean(), method);
				}
			}
		}

		return null;
	}

}
