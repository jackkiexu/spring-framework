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

import java.beans.PropertyEditor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.UriComponentsContributor;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

/**
 * Resolves method arguments annotated with @{@link RequestParam}, arguments of   被 @RequestParam 注解修饰的
 * type {@link MultipartFile} in conjunction with Spring's {@link MultipartResolver} 通过 MultipartResolver 解决 MultipartFile类型
 * abstraction, and arguments of type {@code javax.servlet.http.Part} in conjunction  Servlet 3.0 的 javax.servlet.http.Part
 * with Servlet 3.0 multipart requests. This resolver can also be created in default
 * resolution mode in which simple types (int, long, etc.) not annotated with
 * {@link RequestParam @RequestParam} are also treated as request parameters with
 * the parameter name derived from the argument name.
 *
 * <p>If the method parameter type is {@link Map}, the name specified in the
 * annotation is used to resolve the request parameter String value. The value is
 * then converted to a {@link Map} via type conversion assuming a suitable
 * {@link Converter} or {@link PropertyEditor} has been registered.
 * Or if a request parameter name is not specified the
 * {@link RequestParamMapMethodArgumentResolver} is used instead to provide
 * access to all request parameters in the form of a map.
 *
 * <p>A {@link WebDataBinder} is invoked to apply type conversion to resolved request
 * header values that don't yet match the method parameter type.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.1
 * @see RequestParamMapMethodArgumentResolver
 */
// PS: UriComponentsContributor 中实现的方法在实际运用中很少碰到
public class RequestParamMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver
		implements UriComponentsContributor {

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);

	private final boolean useDefaultResolution;


	/**
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 */
	public RequestParamMethodArgumentResolver(boolean useDefaultResolution) {
		this.useDefaultResolution = useDefaultResolution;
	}

	/**
	 * @param beanFactory a bean factory used for resolving  ${...} placeholder
	 * and #{...} SpEL expressions in default values, or {@code null} if default
	 * values are not expected to contain expressions
	 * @param useDefaultResolution in default resolution mode a method argument
	 * that is a simple type, as defined in {@link BeanUtils#isSimpleProperty},
	 * is treated as a request parameter even if it isn't annotated, the
	 * request parameter name is derived from the method parameter name.
	 */ // beanFactory 主要是解决占位符
	public RequestParamMethodArgumentResolver(ConfigurableBeanFactory beanFactory, boolean useDefaultResolution) {
		super(beanFactory);
		this.useDefaultResolution = useDefaultResolution;
	}


	/**
	 * Supports the following:
	 * <ul>
	 * <li>@RequestParam-annotated method arguments.
	 * This excludes {@link Map} params where the annotation doesn't
	 * specify a name.	See {@link RequestParamMapMethodArgumentResolver}
	 * instead for such params.
	 * <li>Arguments of type {@link MultipartFile}
	 * unless annotated with @{@link RequestPart}.
	 * <li>Arguments of type {@code javax.servlet.http.Part}
	 * unless annotated with @{@link RequestPart}.
	 * <li>In default resolution mode, simple type arguments
	 * even if not with @{@link RequestParam}.
	 * </ul>
	 */
	/** 被 @RequestParam 注解修饰, 但类型不是 Map, 或类型是 Map, 并且 @RequestParam 中指定 name
	 *  被 @RequestPart 注解修饰, 则直接返回 false
	 *  若是 MultipartFile| Part 类型, 则直接返回 true
	 *  若是基础类型, 则返回 true
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {		// 支持简单类型的注解 @RequestParam
		if (parameter.hasParameterAnnotation(RequestParam.class)) {		// 检测参数上面是否含有 RequestParam 注解
			if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) { // 这里的 nestedIfOptional 主要是当参数是用 Optional 时获取真实的参数
				String paramName = parameter.getParameterAnnotation(RequestParam.class).name();
				return StringUtils.hasText(paramName);					// 若是 Map 类型, 且 @RequestParam 中也有 name, 则返回 true
			}
			else {
				return true;
			}
		}
		else {
			if (parameter.hasParameterAnnotation(RequestPart.class)) {	      // 检测参数是否含有 @RequestPart 注解
				return false;
			}
			parameter = parameter.nestedIfOptional();					      // 若参数是用 Optional, 则是用下一层参数
			if (MultipartResolutionDelegate.isMultipartArgument(parameter)) { // 若参数是 MultipartFile 类型, 则返回 true
				return true;
			}
			else if (this.useDefaultResolution) {						      // 若 参数的类型是简单数据类型, 则也返回 true, useDefaultResolution 默认值 true
				return BeanUtils.isSimpleProperty(parameter.getNestedParameterType());
			}
			else {
				return false;
			}
		}
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		// 创建基于 @RequestParam 的 NamedValueInfo
		RequestParam ann = parameter.getParameterAnnotation(RequestParam.class);
		return (ann != null ? new RequestParamNamedValueInfo(ann) : new RequestParamNamedValueInfo());
	}

	@Override
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		/** 整个解析分成两个方向
		 *  1. 从 MultipartHttpServletRequest 中获取数据
		 *  2. 从 HttpServletRequest 中获取数据
		 */
		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);		// 从 NativeWebRequest 中获取 HttpServletRequest
		MultipartHttpServletRequest multipartRequest =												// 从 NativeWebRequest 中获取 MultipartHttpServletRequest
				WebUtils.getNativeRequest(servletRequest, MultipartHttpServletRequest.class);
		// 从 Multipart 角度获取对应数据
		Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
		if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) {	// 若获取到数据, 则返回
			return mpArg;
		}

		Object arg = null;
		if (multipartRequest != null) {		// 尝试从 multipartRequest 中获取 MultipartFile
			List<MultipartFile> files = multipartRequest.getFiles(name);
			if (!files.isEmpty()) {
				arg = (files.size() == 1 ? files.get(0) : files);
			}
		}
		if (arg == null) {
			String[] paramValues = request.getParameterValues(name); // 通过 HttpServletRequest 获取对应的 value
			if (paramValues != null) {
				arg = (paramValues.length == 1 ? paramValues[0] : paramValues);
			}
		}
		return arg;
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {  //上层没有找到值, 则通过这里处理

		HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
		if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {				// 参数是 Multipart, 但 Request 不是 MultipartRequest, 则直接报错
			if (!MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
				throw new MultipartException("Current request is not a multipart request");
			}
			else {
				throw new MissingServletRequestPartException(name);
			}
		}
		else {
			throw new MissingServletRequestParameterException(name,
					parameter.getNestedParameterType().getSimpleName());
		}
	}

	@Override
	public void contributeMethodArgument(MethodParameter parameter, Object value,
			UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService) {

		Class<?> paramType = parameter.getNestedParameterType();
		if (Map.class.isAssignableFrom(paramType) || MultipartFile.class == paramType ||
				"javax.servlet.http.Part".equals(paramType.getName())) {
			return;
		}

		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		String name = (requestParam == null || StringUtils.isEmpty(requestParam.name()) ?
				parameter.getParameterName() : requestParam.name());

		if (value == null) {
			if (requestParam != null) {
				if (!requestParam.required() || !requestParam.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
					return;
				}
			}
			builder.queryParam(name);
		}
		else if (value instanceof Collection) {
			for (Object element : (Collection<?>) value) {
				element = formatUriValue(conversionService, TypeDescriptor.nested(parameter, 1), element);
				builder.queryParam(name, element);
			}
		}
		else {
			builder.queryParam(name, formatUriValue(conversionService, new TypeDescriptor(parameter), value));
		}
	}

	protected String formatUriValue(ConversionService cs, TypeDescriptor sourceType, Object value) {
		if (value == null) {
			return null;
		}
		else if (value instanceof String) {
			return (String) value;
		}
		else if (cs != null) {
			return (String) cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR);
		}
		else {
			return value.toString();
		}
	}


	private static class RequestParamNamedValueInfo extends NamedValueInfo {

		public RequestParamNamedValueInfo() {
			super("", false, ValueConstants.DEFAULT_NONE);
		}

		public RequestParamNamedValueInfo(RequestParam annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
