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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.transform.Source;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ErrorsMethodArgumentResolver;
import org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.annotation.SessionAttributesHandler;
import org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

/**
 * An {@link AbstractHandlerMethodAdapter} that supports {@link HandlerMethod}s
 * with their method argument and return type signature, as defined via
 * {@code @RequestMapping}.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively, to re-configure all argument and return value types,
 * use {@link #setArgumentResolvers} and {@link #setReturnValueHandlers}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter implements BeanFactoryAware, InitializingBean {

	// 参数解析器 HandlerMethodArgumentResolver, 比如 RequestResponseBodyMethodProcessor
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;
	// 组合模式的参数解析器
	private HandlerMethodArgumentResolverComposite argumentResolvers;
	// initBinderArgumentResolvers 的 参数解析器
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;
	// 返回值处理器 比如 RequestResponseBodyMethodProcessor
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;
	// 组合模式的 返回值解析器
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
	// ModelAndView 解析器
	private List<ModelAndViewResolver> modelAndViewResolvers;
	// 内容解析器 <-- 一般通过 HttpServletRequest 获取 MediaType
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();
	// HttpMessageConverter 转换器 --> 比如 form 表单的 FormHttpMessageConverter, Json 对应的 MappingJackson2HttpMessageConverter
	private List<HttpMessageConverter<?>> messageConverters;
	// request|response 对应的 Advice <-- 其实就像 AOP 中的 Advice的概念
	private List<Object> requestResponseBodyAdvice = new ArrayList<Object>();

	private WebBindingInitializer webBindingInitializer;

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;

	private boolean synchronizeOnSession = false;
	// Session 中存储|获取器
	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();
	// 方法解析器, 默认使用 ASM | 反射来获取 <- 反射针对 Java 8
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
	// BeanFactory
	private ConfigurableBeanFactory beanFactory;


	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache =
			new ConcurrentHashMap<Class<?>, SessionAttributesHandler>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<Class<?>, Set<Method>>(64);

	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache =
			new LinkedHashMap<ControllerAdviceBean, Set<Method>>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<Class<?>, Set<Method>>(64);

	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache =
			new LinkedHashMap<ControllerAdviceBean, Set<Method>>();


	public RequestMappingHandlerAdapter() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316
		// 初始化 HttpMessageConverter
		this.messageConverters = new ArrayList<HttpMessageConverter<?>>(4);
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
		this.customArgumentResolvers = argumentResolvers;
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
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null) ? this.argumentResolvers.getResolvers() : null;
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null) ? this.initBinderArgumentResolvers.getResolvers() : null;
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
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return this.returnValueHandlers.getHandlers();
	}

	/**
	 * Provide custom {@link ModelAndViewResolver}s.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver}s, or {@code null}.
	 */
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
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
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used, e.g. 10 seconds on Tomcat with Servlet 3.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		Assert.notNull(interceptors, "CallableProcessingInterceptor List must not be null");
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[interceptors.size()]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		Assert.notNull(interceptors, "DeferredResultProcessingInterceptor List must not be null");
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[interceptors.size()]);
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBody advice beans
		initControllerAdviceCache(); // 初始化 被 @ControllerAdvice 修饰的类 <-- 这是一个 Controller 增强器, 主要是通过 @ModelAttribute, @InitBinder 注解

		if (this.argumentResolvers == null) { // 初始化 HandlerMethodArgumentResolver, 最后封装成 HandlerMethodArgumentResolverComposite <-- 组合模式
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();					// 初始化 HandlerMethodArgumentResolver <-- 参数解析器
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);	// Spring 里面 composite 模式 的提现
		}
		if (this.initBinderArgumentResolvers == null) {														// 初始化initBinderArgumentResolvers <-- 这个平时用得比较少
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();				// 初始化 HandlerMethodReturnValueHandler 比如 -> RequestResponseBodyMethodProcessor
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {  // 获取 ControllerAdvice
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isInfoEnabled()) {
			logger.info("Looking for @ControllerAdvice: " + getApplicationContext());
		}
        // 收集 ApplicationContext 中所有被 @ControllerAdvice 注解修饰的 Bean, 并封装成 ControllerAdviceBean
		List<ControllerAdviceBean> beans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		AnnotationAwareOrderComparator.sort(beans); // 对 ControllerAdviceBean 进行排序

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<Object>();

		for (ControllerAdviceBean bean : beans) { // 获取 Bean 上被 @ModelAttribute @RequestMapping 修饰的方法
			Set<Method> attrMethods = MethodIntrospector.selectMethods(bean.getBeanType(), MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(bean, attrMethods); // 将被 @ModelAttribute @RequestMapping 修饰的方法 放入 modelAttributeAdviceCache
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ModelAttribute methods in " + bean);
				}
			}   // 获取 Bean 上被 @InitBinder 注解的方法
			Set<Method> binderMethods = MethodIntrospector.selectMethods(bean.getBeanType(), INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(bean, binderMethods); // 获取 Bean 上被 @InitBinder 注解的方法, 并放入 initBinderAdviceCache 中
				if (logger.isInfoEnabled()) {
					logger.info("Detected @InitBinder methods in " + bean);
				}
			}
			if (RequestBodyAdvice.class.isAssignableFrom(bean.getBeanType())) { // 若是 RequestBodyAdvice 子类, 则加入 requestResponseBodyAdviceBeans
				requestResponseBodyAdviceBeans.add(bean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected RequestBodyAdvice bean in " + bean);
				}
			}
			if (ResponseBodyAdvice.class.isAssignableFrom(bean.getBeanType())) { // 若是 RequestBodyAdvice 子类, 则加入 requestResponseBodyAdviceBeans
				requestResponseBodyAdviceBeans.add(bean);
				if (logger.isInfoEnabled()) {
					logger.info("Detected ResponseBodyAdvice bean in " + bean);
				}
			}
		}

		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() { // 获取默认的 HandlerMethodArgumentResolver
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();
		// 基于注解的参数解析 <-- 解析的数据来源主要是 HttpServletRequest | ModelAndViewContainer
		// Annotation-based argument resolution
		// 解析被注解 @RequestParam, @RequestPart 修饰的参数, 数据的获取通过 HttpServletRequest.getParameterValues
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		// 解析被注解 @RequestParam 修饰, 且类型是 Map 的参数, 数据的获取通过 HttpServletRequest.getParameterMap
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		// 解析被注解 @PathVariable 修饰, 数据的获取通过 uriTemplateVars, 而 uriTemplateVars 却是通过 RequestMappingInfoHandlerMapping.handleMatch 生成, 其实就是 uri 中映射出的 key <-> value
		resolvers.add(new PathVariableMethodArgumentResolver());
		// 解析被注解 @PathVariable 修饰 且数据类型是 Map, 数据的获取通过 uriTemplateVars, 而 uriTemplateVars 却是通过 RequestMappingInfoHandlerMapping.handleMatch 生成, 其实就是 uri 中映射出的 key <-> value
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		// 解析被注解 @MatrixVariable 修饰, 数据的获取通过 URI提取了;后存储的 uri template 变量值
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		// 解析被注解 @MatrixVariable 修饰 且数据类型是 Map, 数据的获取通过 URI提取了;后存储的 uri template 变量值
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		// 解析被注解 @ModelAttribute 修饰, 且类型是 Map 的参数, 数据的获取通过 ModelAndViewContainer 获取, 通过 DataBinder 进行绑定
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		// 解析被注解 @RequestBody 修饰的参数, 以及被@ResponseBody修饰的返回值, 数据的获取通过 HttpServletRequest 获取, 根据 MediaType通过HttpMessageConverter转换成对应的格式, 在处理返回值时 也是通过 MediaType 选择合适HttpMessageConverter, 进行转换格式, 并输出
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		// 解析被注解 @RequestPart 修饰, 数据的获取通过 HttpServletRequest.getParts()
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		// 解析被注解 @RequestHeader 修饰, 数据的获取通过 HttpServletRequest.getHeaderValues()
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		// 解析被注解 @RequestHeader 修饰且参数类型是 Map, 数据的获取通过 HttpServletRequest.getHeaderValues()
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		// 解析被注解 @CookieValue 修饰, 数据的获取通过 HttpServletRequest.getCookies()
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		// 解析被注解 @Value 修饰, 数据在这里没有解析
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		// 解析被注解 @SessionAttribute 修饰, 数据的获取通过 HttpServletRequest.getAttribute(name, RequestAttributes.SCOPE_SESSION)
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		// 解析被注解 @RequestAttribute 修饰, 数据的获取通过 HttpServletRequest.getAttribute(name, RequestAttributes.SCOPE_REQUEST)
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		// 解析固定类型参数(比如: ServletRequest, HttpSession, InputStream 等), 参数的数据获取还是通过 HttpServletRequest
		resolvers.add(new ServletRequestMethodArgumentResolver());
		// 解析固定类型参数(比如: ServletResponse, OutputStream等), 参数的数据获取还是通过 HttpServletResponse
		resolvers.add(new ServletResponseMethodArgumentResolver());
		// 解析固定类型参数(比如: HttpEntity, RequestEntity 等), 参数的数据获取还是通过 HttpServletRequest
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		// 解析固定类型参数(比如: RedirectAttributes), 参数的数据获取还是通过 HttpServletResponse
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		// 解析固定类型参数(比如: Model等), 参数的数据获取通过 ModelAndViewContainer
		resolvers.add(new ModelMethodProcessor());
		// 解析固定类型参数(比如: Model等), 参数的数据获取通过 ModelAndViewContainer
		resolvers.add(new MapMethodProcessor());
		// 解析固定类型参数(比如: Errors), 参数的数据获取通过 ModelAndViewContainer
		resolvers.add(new ErrorsMethodArgumentResolver());
		// 解析固定类型参数(比如: SessionStatus), 参数的数据获取通过 ModelAndViewContainer
		resolvers.add(new SessionStatusMethodArgumentResolver());
		// 解析固定类型参数(比如: UriComponentsBuilder), 参数的数据获取通过 HttpServletRequest
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * Return the list of argument resolvers to use for {@code @InitBinder}
	 * methods including built-in and custom resolvers.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<HandlerMethodArgumentResolver>();

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<HandlerMethodReturnValueHandler>();

		// Single-purpose return value types
		// 支持 ModelAndView 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 ModelAndViewContainer
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		// 支持 Map 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 ModelAndViewContainer
		handlers.add(new ModelMethodProcessor());
		// 支持 View 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 ModelAndViewContainer
		handlers.add(new ViewMethodReturnValueHandler());
		// 支持 ResponseEntity 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 HttpServletResponse 的数据流中 OutputStream
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters()));
		// 支持 ResponseEntity | StreamingResponseBody 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 HttpServletResponse 的数据流中 OutputStream
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		// 支持 HttpEntity | !RequestEntity 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 HttpServletResponse 的数据流中 OutputStream
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		// 支持 HttpHeaders 类型的 HandlerMethodReturnValueHandler, 最后将数据写入 HttpServletResponse 的数据头部
		handlers.add(new HttpHeadersReturnValueHandler());
		// 支持 Callable 类型的 HandlerMethodReturnValueHandler
		handlers.add(new CallableMethodReturnValueHandler());
		handlers.add(new DeferredResultMethodReturnValueHandler());
		// 支持 WebAsyncTask 类型的 HandlerMethodReturnValueHandler
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		// Annotation-based return value types
		// 将数据加入 ModelAndViewContainer 的 HandlerMethodReturnValueHandler
		handlers.add(new ModelAttributeMethodProcessor(false));
		// 返回值被 ResponseBody 修饰的返回值, 并且根据 MediaType 通过 HttpMessageConverter 转化后进行写入数据流中
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		// 支持返回值为 CharSequence 类型, 设置 ModelAndViewContainer.setViewName
		handlers.add(new ViewNameMethodReturnValueHandler());
		// 支持返回值为 Map, 并将结果设置到 ModelAndViewContainer
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		// 直接返回 true, 表明 handler 为HandlerMethod 对象即可
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		// 确定请求是否符合, 比如 是否 GET/POST 请求, 可配置
		checkRequest(request);

		// Execute invokeHandlerMethod in synchronized block if required.
		if (this.synchronizeOnSession) { // 同步执行 请求, 这里的同步指基于 HttpSession 进行同步
			HttpSession session = request.getSession(false);        // 获取 HttpServletRequest 对应的 HttpSession
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);	// 获取 HttpSession  <-- 请求的处理针对 mutex 进行同步
				synchronized (mutex) {								// 激活方法 HandlerMethod
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {													// 不基于 HttpSession 的方式激活 HandlerMethod
				// No HttpSession available -> no mutex necessary
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {// 不基于 HttpSession 的方式激活 HandlerMethod
			// 最终通过 invokeHandlerMethod() 方法创建 ModelAndView视图对象, 这里涉及到反射机制使用
			// No synchronization on session demanded at all...
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		// 设置关于 cache 的头部信息
		if (!response.containsHeader(HEADER_CACHE_CONTROL)) { // 若 Http 头部含有 Cache-Control
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) { // 设置 Http 中的 cache 设置
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);					  // 设置 Http 中 Expires 等信息
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler type
	 * (never {@code null}).
	 */
	//
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		Class<?> handlerType = handlerMethod.getBeanType();
		// 先从缓存中换取 Session 存储|获取器
		SessionAttributesHandler sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
		// 若缓存中不存在, 则创建一个
		if (sessionAttrHandler == null) {
			synchronized (this.sessionAttributesHandlerCache) {
				sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
				if (sessionAttrHandler == null) { // 封装 Session 存储|获取器
					sessionAttrHandler = new SessionAttributesHandler(handlerType, sessionAttributeStore);
					this.sessionAttributesHandlerCache.put(handlerType, sessionAttrHandler);
				}
			}
		}
		return sessionAttrHandler;
	}

	/**
	 * Invoke the {@link RequestMapping} handler method preparing a {@link ModelAndView}
	 * if view resolution is required.
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
		// 构建 ServletWebRequest <-- 主要由 HttpServletRequest, HttpServletResponse
		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);       // 构建 DataBinder 工厂
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);      // binderFactory 中存储着被 @InitBinder, @ModelAttribute 修饰的方法 <- 最终包裹成 InvocableHandlerMethod

			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod); // 构建一个 ServletInvocableHandlerMethod
			invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);		// 设置方法参数解析器 HandlerMethodArgumentValueResolver
			invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);  // 返回值处理器 HandlerMethodReturnValueHandler
			invocableMethod.setDataBinderFactory(binderFactory);							// 设置 WebDataBinderFactory
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);		// 设置 参数名解析器

			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));   // 获取 HttpServletRequest 中存储的 FlashMap
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);				// 这里是激活 @ModelAttribute, @InitBinder 方法, 并将返回值放入 ModelAndViewContainer
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);
			////////////////////////// 下面是异步处理那部分
			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);
			////////////////////////// 下面是异步处理那部分
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				if (logger.isDebugEnabled()) {
					logger.debug("Found concurrent result value [" + result + "]");
				}
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}

			invocableMethod.invokeAndHandle(webRequest, mavContainer);		// 将 HttpServletRequest 转换成方法的参数, 激活方法, 最后 通过 HandlerMethodReturnValueHandler 来处理返回值
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}

			return getModelAndView(mavContainer, modelFactory, webRequest); // 生成 ModelAndView
		}
		finally {
			webRequest.requestCompleted(); // 标志请求已经结束, 进行一些生命周期回调函数的激活
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given {@link HandlerMethod} definition.
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {  // 构建一个 ServletInvocableHandlerMethod
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);   // 搜索被 @ModelAttribute 修饰的 Method
			this.modelAttributeCache.put(handlerType, methods);			  //
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<InvocableHandlerMethod>();
		// Global methods first
		for (Entry<ControllerAdviceBean, Set<Method>> entry : this.modelAttributeAdviceCache.entrySet()) {
			if (entry.getKey().isApplicableToBeanType(handlerType)) {
				Object bean = entry.getKey().resolveBean();
				for (Method method : entry.getValue()) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		}
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		Class<?> handlerType = handlerMethod.getBeanType();				// 获取 HandlerMethod 所属的 Bean
		Set<Method> methods = this.initBinderCache.get(handlerType);    // initBinderCache 中存储 被 @InitBinder 修饰的 Method
		if (methods == null) {
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS); // 获取 handlerType 中被 @InitBinder 修饰的 Method
			this.initBinderCache.put(handlerType, methods);             // 将 被 @InitBinder 修饰的 Method 放入 initBinderCache
		}
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<InvocableHandlerMethod>();
		// Global methods first
		for (Entry<ControllerAdviceBean, Set<Method>> entry : this.initBinderAdviceCache.entrySet()) { // 全局配置的, 被 @InitBinder修饰的方法
			if (entry.getKey().isApplicableToBeanType(handlerType)) {
				Object bean = entry.getKey().resolveBean();
				for (Method method : entry.getValue()) {
					initBinderMethods.add(createInitBinderMethod(bean, method)); // 创建被 @InitBinder 可执行的 HandlerMethod
				}
			}
		}
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));  // 创建 InitBinderMethod <-- 就是被 @InitBinder 修饰
		}
		return createDataBinderFactory(initBinderMethods);
	}
	// 创建 被 @InitBinder 修饰的方法, 包裹成 InvocableHandlerMethod (可激活的 HandlerMethod)
	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	// 构建视图返回对象
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {
		// 通过 sessionAttributesHandler 工具类将 HttpServletRequest 里面的属性值 设置到 ModelAndViewContainer.ModelMap 里面
		modelFactory.updateModel(webRequest, mavContainer);
		if (mavContainer.isRequestHandled()) { // 是否请求被处理过了, 是的话 视图解析就不需要了
			return null;
		}
		ModelMap model = mavContainer.getModel();
		// 通过 viewName, ModelAndViewContainer.ModelMap, ModelAndViewContainer.getStatus 构成 ModelAndView 对象
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}
		if (model instanceof RedirectAttributes) {	// 若是重新定向的请求, 则将这次请求的 RedirectAttributes.flashAttributes 放入 FlashMap 中
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
		}
		return mav;
	}


	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 */
	public static final MethodFilter INIT_BINDER_METHODS = new MethodFilter() {
		@Override
		public boolean matches(Method method) {  // 方法上有 InitBinder 注解
			return AnnotationUtils.findAnnotation(method, InitBinder.class) != null;
		}
	};

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = new MethodFilter() {
		@Override
		public boolean matches(Method method) {  // 方法上又 RequestMapping, 但没有 ModelAttributes
			return ((AnnotationUtils.findAnnotation(method, RequestMapping.class) == null) &&
					(AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null));
		}
	};

}
