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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.handler.AbstractDetectingUrlHandlerMapping;

/**
 * Implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface that maps handlers based on HTTP paths expressed through the  通过 RequestMapping 注解
 * {@link RequestMapping} annotation at the type or method level.
 *
 * <p>Registered by default in {@link org.springframework.web.servlet.DispatcherServlet}
 * on Java 5+. <b>NOTE:</b> If you define custom HandlerMapping beans in your
 * DispatcherServlet context, you need to add a DefaultAnnotationHandlerMapping bean
 * explicitly(明确的), since custom HandlerMapping beans replace the default mapping strategies.
 * Defining a DefaultAnnotationHandlerMapping also allows for registering custom
 * interceptors:
 *
 * <pre class="code">
 * &lt;bean class="org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping"&gt;
 *   &lt;property name="interceptors"&gt;
 *     ...
 *   &lt;/property&gt;
 * &lt;/bean&gt;</pre>
 *
 * Annotated controllers are usually marked with the {@link Controller} stereotype
 * at the type level. This is not strictly necessary when {@link RequestMapping} is
 * applied at the type level (since such a handler usually implements the
 * {@link org.springframework.web.servlet.mvc.Controller} interface). However,
 * {@link Controller} is required for detecting {@link RequestMapping} annotations
 * at the method level if {@link RequestMapping} is not present at the type level.
 *
 * <p><b>NOTE:</b> Method-level mappings are only allowed to narrow the mapping
 * expressed at the class level (if any). HTTP paths need to uniquely map onto
 * specific handler beans, with any given HTTP path only allowed to be mapped
 * onto one specific handler bean (not spread across multiple handler beans).
 * It is strongly recommended to co-locate related handler methods into the same bean.
 *
 * <p>The {@link AnnotationMethodHandlerAdapter} is responsible for processing
 * annotated handler methods, as mapped by this HandlerMapping. For
 * {@link RequestMapping} at the type level, specific HandlerAdapters such as
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter} apply.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 2.5
 * @see RequestMapping
 * @see AnnotationMethodHandlerAdapter
 * @deprecated as of Spring 3.2, in favor of
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping RequestMappingHandlerMapping}
 */
@Deprecated
public class DefaultAnnotationHandlerMapping extends AbstractDetectingUrlHandlerMapping {

	static final String USE_DEFAULT_SUFFIX_PATTERN = DefaultAnnotationHandlerMapping.class.getName() + ".useDefaultSuffixPattern";

	private boolean useDefaultSuffixPattern = true;
	// 缓存的 class 与 RequestMapping 的映射关系
	private final Map<Class<?>, RequestMapping> cachedMappings = new HashMap<Class<?>, RequestMapping>();


	/**
	 * Set whether to register paths using the default suffix pattern as well:
	 * i.e. whether "/users" should be registered as "/users.*" and "/users/" too.
	 * <p>Default is "true". Turn this convention off if you intend to interpret
	 * your {@code @RequestMapping} paths strictly.
	 * <p>Note that paths which include a ".xxx" suffix or end with "/" already will not be
	 * transformed using the default suffix pattern in any case.
	 */
	public void setUseDefaultSuffixPattern(boolean useDefaultSuffixPattern) {		// 是否用后缀 <- 这里指加载 url 后面的附加路径
		this.useDefaultSuffixPattern = useDefaultSuffixPattern;
	}


	/**
	 * Checks for presence of the {@link org.springframework.web.bind.annotation.RequestMapping}
	 * annotation on the handler class and on any of its methods.
	 */
	@Override
	protected String[] determineUrlsForHandler(String beanName) {
		ApplicationContext context = getApplicationContext();
		Class<?> handlerType = context.getType(beanName);			  // 获取 beanName 对应的 handler
		RequestMapping mapping = context.findAnnotationOnBean(beanName, RequestMapping.class); // 获取指定 beanName 上 RequestMapping 的注解信息
		if (mapping != null) {
			// @RequestMapping found at type level
			this.cachedMappings.put(handlerType, mapping);
			Set<String> urls = new LinkedHashSet<String>();
			String[] typeLevelPatterns = mapping.value();
			if (typeLevelPatterns.length > 0) {
				// @RequestMapping specifies paths at type level	  // 从类级别开始解析 方法
				String[] methodLevelPatterns = determineUrlsForHandlerMethods(handlerType, true);
				for (String typeLevelPattern : typeLevelPatterns) {
					if (!typeLevelPattern.startsWith("/")) {		  // 若注解 url 不是以 "/" 则加上 "/"
						typeLevelPattern = "/" + typeLevelPattern;
					}
					boolean hasEmptyMethodLevelMappings = false;
					for (String methodLevelPattern : methodLevelPatterns) {
						if (methodLevelPattern == null) {
							hasEmptyMethodLevelMappings = true;		  // hasEmptyMethodLevelMappings 表示是否存在 没有设置 url 的 RequestMapping
						}
						else {										  // 组合 类级别的 url 与 方法级别的 url
							String combinedPattern = getPathMatcher().combine(typeLevelPattern, methodLevelPattern);
							addUrlsForPath(urls, combinedPattern);	  // 将组合后的 url 加入 urls
						}
					}
					if (hasEmptyMethodLevelMappings ||
							org.springframework.web.servlet.mvc.Controller.class.isAssignableFrom(handlerType)) {
						addUrlsForPath(urls, typeLevelPattern);		 // 若存在 "没有设置url的RequestMapping" -> 则 method 处理的就是注释在 Class 上的 url
					}
				}
				return StringUtils.toStringArray(urls);				 // 返回能处理的 url
			}
			else {													 // 若class上是"没设置url的RequestMapping", 则直接返回 determineUrlsForHandlerMethods 解析的 url
				// actual paths specified by @RequestMapping at method level
				return determineUrlsForHandlerMethods(handlerType, false);
			}
		}															// 若 handler 被 Controller 注解, 则从方法级别获取 RequestMapping 来注册信息
		else if (AnnotationUtils.findAnnotation(handlerType, Controller.class) != null) {
			// @RequestMapping to be introspected at method level
			return determineUrlsForHandlerMethods(handlerType, false);
		}
		else {
			return null;
		}
	}

	/**
	 * Derive(起源, 获得) URL mappings from the handler's method-level mappings.
	 * @param handlerType the handler type to introspect
	 * @param hasTypeLevelMapping whether the method-level mappings are nested
	 * within a type-level mapping
	 * @return the array of mapped URLs
	 */
	protected String[] determineUrlsForHandlerMethods(Class<?> handlerType, final boolean hasTypeLevelMapping) {
		String[] subclassResult = determineUrlsForHandlerMethods(handlerType);	//  默认返回 null
		if (subclassResult != null) {
			return subclassResult;
		}

		final Set<String> urls = new LinkedHashSet<String>();
		Set<Class<?>> handlerTypes = new LinkedHashSet<Class<?>>();
		handlerTypes.add(handlerType);							// 获取类, 及接口
		handlerTypes.addAll(Arrays.asList(handlerType.getInterfaces()));
		for (Class<?> currentHandlerType : handlerTypes) {		// 解析 handler类及接口 中的所有方法 -> 解析其中被 RequestMapping 的方法
			ReflectionUtils.doWithMethods(currentHandlerType, new ReflectionUtils.MethodCallback() {
				@Override
				public void doWith(Method method) {				// 获取方法上的 RequestMapping 注解信息
					RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
					if (mapping != null) {
						String[] mappedPatterns = mapping.value();
						if (mappedPatterns.length > 0) {
							for (String mappedPattern : mappedPatterns) { // 下面的 hasTypeLevelMapping 表示是否已经有了方法级别的 RequestMapping
								if (!hasTypeLevelMapping && !mappedPattern.startsWith("/")) {
									mappedPattern = "/" + mappedPattern;
								}
								addUrlsForPath(urls, mappedPattern);	 // 将请求路径加入 urls
							}
						}
						else if (hasTypeLevelMapping) {
							// empty method-level RequestMapping
							urls.add(null);
						}
					}
				}
			}, ReflectionUtils.USER_DECLARED_METHODS);
		}
		return StringUtils.toStringArray(urls);
	}

	/**
	 * Derive(获得) URL mappings from the handler's method-level mappings.
	 * @param handlerType the handler type to introspect (内省)
	 * @return the array of mapped URLs
	 */
	protected String[] determineUrlsForHandlerMethods(Class<?> handlerType) {
		return null;
	}

	/**
	 * Add URLs and/or URL patterns for the given path.
	 * @param urls the Set of URLs for the current bean
	 * @param path the currently introspected path
	 */
	protected void addUrlsForPath(Set<String> urls, String path) {
		urls.add(path);
		if (this.useDefaultSuffixPattern && path.indexOf('.') == -1 && !path.endsWith("/")) {
			urls.add(path + ".*");
			urls.add(path + "/");
		}
	}


	/**
	 * Validate the given annotated handler against the current request.
	 * @see #validateMapping
	 */
	@Override
	protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
		RequestMapping mapping = this.cachedMappings.get(handler.getClass());
		if (mapping == null) {
			mapping = AnnotationUtils.findAnnotation(handler.getClass(), RequestMapping.class);
		}
		if (mapping != null) {
			validateMapping(mapping, request);
		}
		request.setAttribute(USE_DEFAULT_SUFFIX_PATTERN, this.useDefaultSuffixPattern);
	}

	/**
	 * Validate the given type-level mapping metadata against the current request,
	 * checking HTTP request method and parameter conditions.
	 * @param mapping the mapping metadata to validate
	 * @param request current HTTP request
	 * @throws Exception if validation failed
	 */
	protected void validateMapping(RequestMapping mapping, HttpServletRequest request) throws Exception {
		RequestMethod[] mappedMethods = mapping.method();
		if (!ServletAnnotationMappingUtils.checkRequestMethod(mappedMethods, request)) {
			String[] supportedMethods = new String[mappedMethods.length];
			for (int i = 0; i < mappedMethods.length; i++) {
				supportedMethods[i] = mappedMethods[i].name();
			}
			throw new HttpRequestMethodNotSupportedException(request.getMethod(), supportedMethods);
		}

		String[] mappedParams = mapping.params();
		if (!ServletAnnotationMappingUtils.checkParameters(mappedParams, request)) {
			throw new UnsatisfiedServletRequestParameterException(mappedParams, request.getParameterMap());
		}

		String[] mappedHeaders = mapping.headers();
		if (!ServletAnnotationMappingUtils.checkHeaders(mappedHeaders, request)) {
			throw new ServletRequestBindingException("Header conditions \"" +
					StringUtils.arrayToDelimitedString(mappedHeaders, ", ") +
					"\" not met for actual request");
		}
	}

	@Override
	protected boolean supportsTypeLevelMappings() {
		return true;
	}
}
