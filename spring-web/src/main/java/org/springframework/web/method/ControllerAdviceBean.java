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

package org.springframework.web.method;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Encapsulates information about an {@linkplain ControllerAdvice @ControllerAdvice}
 * Spring-managed bean without necessarily requiring it to be instantiated.
 *
 * <p>The {@link #findAnnotatedBeans(ApplicationContext)} method can be used to
 * discover such beans. However, a {@code ControllerAdviceBean} may be created
 * from any object, including ones without an {@code @ControllerAdvice}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @since 3.2
 */
public class ControllerAdviceBean implements Ordered {
	// 被 @ControllerAdvice 修饰的 Bean
	private final Object bean;
	// 工厂类
	private final BeanFactory beanFactory;
	// ControllerAdvice 的优先级
	private final int order;
	// 这个 @ControllerAdvice 只增强 basePackages 目录下面的类
	private final Set<String> basePackages;
	// 这个 @ControllerAdvice 只增强 assignableTypes 这些类别的类
	private final List<Class<?>> assignableTypes;
	// 这个 @ControllerAdvice 只增强 被这些注解修饰的类
	private final List<Class<? extends Annotation>> annotations;


	/**
	 * Create a {@code ControllerAdviceBean} using the given bean instance.
	 * @param bean the bean instance
	 */
	public ControllerAdviceBean(Object bean) {
		this(bean, null);
	}

	/**
	 * Create a {@code ControllerAdviceBean} using the given bean name.
	 * @param beanName the name of the bean
	 * @param beanFactory a BeanFactory that can be used later to resolve the bean
	 */
	public ControllerAdviceBean(String beanName, BeanFactory beanFactory) {
		this((Object) beanName, beanFactory);
	}

	private ControllerAdviceBean(Object bean, BeanFactory beanFactory) {
		this.bean = bean;
		this.beanFactory = beanFactory;
		Class<?> beanType;

		if (bean instanceof String) { // 若是 String 的话, 直接从 ApplicationContext 获取对应的真实 Bean
			String beanName = (String) bean;
			Assert.hasText(beanName, "Bean name must not be null");
			Assert.notNull(beanFactory, "BeanFactory must not be null");
			if (!beanFactory.containsBean(beanName)) {
				throw new IllegalArgumentException("BeanFactory [" + beanFactory +
						"] does not contain specified controller advice bean '" + beanName + "'");
			}
			beanType = this.beanFactory.getType(beanName); // 获取真实 Bean 的类型
			this.order = initOrderFromBeanType(beanType);  // 获取对应优先级 <- 可能存在多个 @ControllerAdvice
		}
		else {
			Assert.notNull(bean, "Bean must not be null");
			beanType = bean.getClass();
			this.order = initOrderFromBean(bean);
		}

		ControllerAdvice annotation =						// 获取 @ControllerAdvice 注解的信息
				AnnotatedElementUtils.findMergedAnnotation(beanType, ControllerAdvice.class);

		if (annotation != null) {
			this.basePackages = initBasePackages(annotation); // 设置 basePackages <-- 这个 @ControllerAdvice 只修饰这个包下面的类
			this.assignableTypes = Arrays.asList(annotation.assignableTypes()); // 设置 assignableTypes <-- 这个 @ControllerAdvice 只修饰 assignableTypes 这些类别的类
			this.annotations = Arrays.asList(annotation.annotations());         // 设置 annotations <-- 这个 @ControllerAdvice 只修饰被这些 annotations 注解修饰的类
		}
		else {
			this.basePackages = Collections.emptySet();
			this.assignableTypes = Collections.emptyList();
			this.annotations = Collections.emptyList();
		}
	}


	/**
	 * Returns the order value extracted from the {@link ControllerAdvice}
	 * annotation, or {@link Ordered#LOWEST_PRECEDENCE} otherwise.
	 */
	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Return the type of the contained bean.
	 * <p>If the bean type is a CGLIB-generated class, the original
	 * user-defined class is returned.
	 */
	public Class<?> getBeanType() {
		Class<?> clazz = (this.bean instanceof String ?
				this.beanFactory.getType((String) this.bean) : this.bean.getClass());
		return ClassUtils.getUserClass(clazz);
	}

	/**
	 * Return a bean instance if necessary resolving the bean name through the BeanFactory.
	 */
	public Object resolveBean() {
		return (this.bean instanceof String ? this.beanFactory.getBean((String) this.bean) : this.bean);
	}

	/**
	 * Check whether the given bean type should be assisted by this
	 * {@code @ControllerAdvice} instance.
	 * @param beanType the type of the bean to check
	 * @see org.springframework.web.bind.annotation.ControllerAdvice
	 * @since 4.0
	 */
	public boolean isApplicableToBeanType(Class<?> beanType) { // 可适用, 可运用, 检测 @ControllerAdvice 是否适配这个类
		if (!hasSelectors()) {
			return true;
		}
		else if (beanType != null) {
			for (String basePackage : this.basePackages) {		// 通过 @ControllerAdvice注解中的 basePackages 来进行过滤
				if (beanType.getName().startsWith(basePackage)) {
					return true;
				}
			}
			for (Class<?> clazz : this.assignableTypes) {       // 通过 @ControllerAdvice注解中的 assignableTypes 来进行过滤
				if (ClassUtils.isAssignable(clazz, beanType)) {
					return true;
				}
			}
			for (Class<? extends Annotation> annotationClass : this.annotations) { // 通过 @ControllerAdvice注解中的 annotations 来进行过滤
				if (AnnotationUtils.findAnnotation(beanType, annotationClass) != null) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasSelectors() {	// 是否有过滤条件
		return (!this.basePackages.isEmpty() || !this.assignableTypes.isEmpty() || !this.annotations.isEmpty());
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ControllerAdviceBean)) {
			return false;
		}
		ControllerAdviceBean otherAdvice = (ControllerAdviceBean) other;
		return (this.bean.equals(otherAdvice.bean) && this.beanFactory == otherAdvice.beanFactory);
	}

	@Override
	public int hashCode() {
		return this.bean.hashCode();
	}

	@Override
	public String toString() {
		return this.bean.toString();
	}


	/**
	 * Find the names of beans annotated with
	 * {@linkplain ControllerAdvice @ControllerAdvice} in the given
	 * ApplicationContext and wrap them as {@code ControllerAdviceBean} instances.
	 */ // 收集 ApplicationContext 中所有被 @ControllerAdvice 注解修饰的 Bean, 并封装成 ControllerAdviceBean
	public static List<ControllerAdviceBean> findAnnotatedBeans(ApplicationContext applicationContext) {
		List<ControllerAdviceBean> beans = new ArrayList<ControllerAdviceBean>();
		for (String name : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(applicationContext, Object.class)) {		// 获取容器中所有 类的名称
			if (applicationContext.findAnnotationOnBean(name, ControllerAdvice.class) != null) {						// 查看 name 对应 Class 上面有没有修饰  注解 ControllerAdvice
				beans.add(new ControllerAdviceBean(name, applicationContext));											// 若存在的话, 封装成 ControllerAdviceBean 对象
			}
		}
		return beans;
	}

	private static int initOrderFromBean(Object bean) {
		return (bean instanceof Ordered ? ((Ordered) bean).getOrder() : initOrderFromBeanType(bean.getClass()));
	}

	private static int initOrderFromBeanType(Class<?> beanType) {
		return OrderUtils.getOrder(beanType, Ordered.LOWEST_PRECEDENCE);
	}

	private static Set<String> initBasePackages(ControllerAdvice annotation) {
		Set<String> basePackages = new LinkedHashSet<String>();
		for (String basePackage : annotation.basePackages()) {		// 获取 @ControllerAdvice 中的 basePackage 属性
			if (StringUtils.hasText(basePackage)) {
				basePackages.add(adaptBasePackage(basePackage));
			}
		}
		for (Class<?> markerClass : annotation.basePackageClasses()) { // 获取 basePackageClasses 中 class 所在的package
			basePackages.add(adaptBasePackage(ClassUtils.getPackageName(markerClass))); // ClassUtils.getPackageName -> 获取类的包名
		}
		return basePackages;
	}

	private static String adaptBasePackage(String basePackage) {
		return (basePackage.endsWith(".") ? basePackage : basePackage + ".");
	}

}
