/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

/**
 * 上线文信息 用于 @Conditional
 * Context information for use by {@link Condition}s.
 *
 * @author Phillip Webb
 * @since 4.0
 */
public interface ConditionContext {

	/**
	 * 获取 BeanDefinition 注册器, 一般用于获取满足条件的  Bean
	 * Return the {@link BeanDefinitionRegistry} that will hold the bean definition
	 * should the condition match or {@code null} if the registry is not available.
	 * @return the registry or {@code null}
	 */
	BeanDefinitionRegistry getRegistry();

	/**
	 * 返回 BeanFactory
	 * Return the {@link ConfigurableListableBeanFactory} that will hold the bean
	 * definition should the condition match or {@code null} if the bean factory
	 * is not available.
	 * @return the bean factory or {@code null}
	 */
	ConfigurableListableBeanFactory getBeanFactory();

	/**
	 * 返回当前程序的环境
	 * Return the {@link Environment} for which the current application is running
	 * or {@code null} if no environment is available.
	 * @return the environment or {@code null}
	 */
	Environment getEnvironment();

	/**
	 * 返回当前程序的资源加载器
	 * Return the {@link ResourceLoader} currently being used or {@code null}
	 * if the resource loader cannot be obtained.
	 * @return a resource loader or {@code null}
	 */
	ResourceLoader getResourceLoader();

	/** 返回加载 Conditional 的 ClassLoader
	 * Return the {@link ClassLoader} that should be used to load additional
	 * classes or {@code null} if the default classloader should be used.
	 * @return the class loader or {@code null}
	 */
	ClassLoader getClassLoader();

}
