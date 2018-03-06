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

package org.springframework.web.method.annotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletException;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Abstract base class for resolving method arguments from a named value.  <-- 为解决基于 name 的参数解决
 * Request parameters, request headers, and path variables are examples of named <-- 数据的获取一般通过 HttpServletRequest, Http Headers, URI template variables
 * values. Each may have a name, a required flag, and a default value.
 *
 * <p>Subclasses define how to do the following:  子类做的事情
 * <ul>
 * <li>Obtain named value information for a method parameter // 从 Method Parameter 中获取 NameValueinfo 对象
 * <li>Resolve names into argument values
 * <li>Handle missing argument values when argument values are required 处理这种情况 -> 若值是必需的, 但value又没有获取
 * <li>Optionally handle a resolved value
 * </ul>
 *
 * <p>A default value string can contain ${...} placeholders(占位符) and Spring Expression
 * Language #{...} expressions. For this to work a
 * {@link ConfigurableBeanFactory} must be supplied to the class constructor.
 *
 * <p>A {@link WebDataBinder} is created to apply type conversion to the resolved
 * argument value if it doesn't match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private final ConfigurableBeanFactory configurableBeanFactory;

	private final BeanExpressionContext expressionContext;

	// 存放 MethodParameter <--> NameValueInfo 的缓存
	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache =
			new ConcurrentHashMap<MethodParameter, NamedValueInfo>(256);


	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}

	/**
	 * @param beanFactory a bean factory to use for resolving ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 */
	public AbstractNamedValueMethodArgumentResolver(ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		this.expressionContext =
				(beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}


	@Override
	public final Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);		// 创建 MethodParameter 对应的 NamedValueInfo
		MethodParameter nestedParameter = parameter.nestedIfOptional();		// Java 8 中支持的 java.util.Optional

		Object resolvedName = resolveStringValue(namedValueInfo.name);		// 因为此时的 name 可能还是被 ${} 符号包裹, 则通过 BeanExpressionResolver 来进行解析
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}
		// 下面的数据大体通过 HttpServletRequest, Http Headers, URI template variables(URI 模版变量) 获取
		// @PathVariable     --> 通过前期对 uri 解析后得到的 decodedUriVariables 获得
		// @RequestParam     --> 通过 HttpServletRequest.getParameterValues(name) 获取
		// @RequestAttribute --> 通过 HttpServletRequest.getAttribute(name) 获取   <-- 这里的 scope 是 request
		// @RequestHeader    --> 通过 HttpServletRequest.getHeaderValues(name) 获取
		// @CookieValue      --> 通过 HttpServletRequest.getCookies() 获取
		// @SessionAttribute --> 通过 HttpServletRequest.getAttribute(name) 获取 <-- 这里的 scope 是 session
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);		// 通过 上面的 resolvedName  <-- 模版方法
		if (arg == null) {
			if (namedValueInfo.defaultValue != null) {					// 若 arg == null, 则使用 defaultValue, 这里默认值可能也是通过占位符 ${...} 来进行查找
				arg = resolveStringValue(namedValueInfo.defaultValue);
			}
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {	// 若 arg == null && defaultValue == null && 非 optional 类型的参数 则通过 handleMissingValue 来进行处理, 一般是报异常
				handleMissingValue(namedValueInfo.name, nestedParameter, webRequest);
			}
			arg = handleNullValue(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());  // 对 null 值的处理 一般还是报yichang
		}
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) { // 若得到的数据是 "", 则还是使用默认值
			arg = resolveStringValue(namedValueInfo.defaultValue);		// 这里的默认值有可能也是 ${} 修饰的, 所以也需要通过 BeanExpressionResolver 来进行解析
		}

		if (binderFactory != null) {
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
			try {	// 通过 WebDataBinder 中的 Converter 将 arg 转换成 parameter.getParameterType() 对应的类型
				arg = binder.convertIfNecessary(arg, parameter.getParameterType(), parameter); // 将 arg 转换成 parameter.getParameterType() 类型, 这里就需要 SimpleTypeConverter
			}
			catch (ConversionNotSupportedException ex) {
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());

			}
		}
		// 这里的 handleResolvedValue 一般是空实现, 在PathVariableMethodArgumentResolver中也是存储一下数据到 HttpServletRequest 中
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}

	/**
	 * Obtain the named value for the given method parameter.
	 */
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {		  // 创建 MethodParameter 对应的 NamedValueInfo
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);  // 根据 MethodParameter 获取对应的 NamedValueInfo
		if (namedValueInfo == null) {
			namedValueInfo = createNamedValueInfo(parameter);					  // 创建 NamedValueInfo <- 这是一个模版方法, 交给子类实现
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			this.namedValueInfoCache.put(parameter, namedValueInfo);			  // 将 MethodParameter <--> NamedValueInfo 放入缓存中
		}
		return namedValueInfo;
	}

	/**
	 * Create the {@link NamedValueInfo} object for the given method parameter. Implementations typically
	 * retrieve the method annotation by means of {@link MethodParameter#getParameterAnnotation(Class)}.
	 * @param parameter the method parameter
	 * @return the named value information
	 */
	// 模版方法, 留给子类创建 NamedValueInfo
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	/**
	 * Create a new NamedValueInfo based on the given NamedValueInfo with sanitized values.
	 */
	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {			// 这个方法主要是为了 获取 参数的名称, 以及 对应的 默认 defaultValue
		String name = info.name;
		if (info.name.isEmpty()) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument type [" + parameter.getNestedParameterType().getName() +
						"] not available, and parameter name information not found in class file either.");
			}
		}
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}

	/**
	 * Resolve the given annotation-specified value,
	 * potentially containing placeholders and expressions.
	 */
	private Object resolveStringValue(String value) {				// 绝对的夸张, 这里还支持 Spring de 表达式解析参数名称
		if (this.configurableBeanFactory == null) {
			return value;
		}
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null) {
			return value;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}

	/**
	 * Resolve the given parameter type and value name into an argument value.
	 * @param name the name of the value being resolved
	 * @param parameter the method parameter to resolve to an argument value
	 * (pre-nested in case of a {@link java.util.Optional} declaration)
	 * @param request the current request
	 * @return the resolved argument (may be {@code null})
	 * @throws Exception in case of errors
	 */
	// 留给子类 <-- 模版方法, 获取参数对应的数据, 一般都是通过 HttpServletRequest 中的数据获取
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 * @param request the current request
	 * @since 4.3
	 */
	// 为找到合适的参数怎么办 -> 模版方法, 子类一般是报异常
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}

	/**
	 * Invoked when a named value is required, but {@link #resolveName(String, MethodParameter, NativeWebRequest)}
	 * returned {@code null} and there is no default value. Subclasses typically throw an exception in this case.
	 * @param name the name for the value
	 * @param parameter the method parameter
	 */
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	/**
	 * A {@code null} results in a {@code false} value for {@code boolean}s or an exception for other primitives.
	 */
	private Object handleNullValue(String name, Object value, Class<?> paramType) {
		if (value == null) {
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}

	/**
	 * Invoked after a value is resolved.
	 * @param arg the resolved argument value
	 * @param name the argument name
	 * @param parameter the argument parameter type
	 * @param mavContainer the {@link ModelAndViewContainer} (may be {@code null})
	 * @param webRequest the current request
	 */
	protected void handleResolvedValue(Object arg, String name, MethodParameter parameter,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}


	/**
	 * Represents the information about a named value, including name, whether it's required and a default value.
	 */
	protected static class NamedValueInfo {
		// 参数的名称
		private final String name;
		// 是否参数是必需的
		private final boolean required;
		// 参数的默认值
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
