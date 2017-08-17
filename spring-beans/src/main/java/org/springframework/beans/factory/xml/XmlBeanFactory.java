/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.beans.factory.xml;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.Resource;

/**
 * Convenience extension of {@link DefaultListableBeanFactory} that reads bean definitions
 * from an XML document. Delegates to {@link XmlBeanDefinitionReader} underneath; effectively
 * equivalent to using an XmlBeanDefinitionReader with a DefaultListableBeanFactory.
 *
 * <p>The structure, element and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). "beans" doesn't need to be the root element of the XML
 * document: This class will parse all bean definition elements in the XML file.
 *
 * <p>This class registers each bean definition with the {@link DefaultListableBeanFactory}
 * superclass, and relies on the latter's implementation of the {@link BeanFactory} interface.
 * It supports singletons, prototypes, and references to either of these kinds of bean.
 * See {@code "spring-beans-3.x.xsd"} (or historically, {@code "spring-beans-2.0.dtd"}) for
 * details on options and configuration style.
 *
 * <p><b>For advanced needs, consider using a {@link DefaultListableBeanFactory} with
 * an {@link XmlBeanDefinitionReader}.</b> The latter allows for reading from multiple XML
 * resources and is highly configurable in its actual XML parsing behavior.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 15 April 2001
 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory
 * @see XmlBeanDefinitionReader
 * @deprecated as of Spring 3.1 in favor of {@link DefaultListableBeanFactory} and
 * {@link XmlBeanDefinitionReader}
 *
 * 参考资料
 * http://www.cnblogs.com/VergiLyn/p/6130188.html
 */
@Deprecated
@SuppressWarnings({"serial", "all"})
public class XmlBeanFactory extends DefaultListableBeanFactory {

	private final XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(this);


	/**
	 * Create a new XmlBeanFactory with the given resource,
	 * which must be parsable using DOM.
	 * @param resource XML resource to load bean definitions from
	 * @throws BeansException in case of loading or parsing errors
	 */
	public XmlBeanFactory(Resource resource) throws BeansException {
		this(resource, null);
	}

	/**
	 * Create a new XmlBeanFactory with the given input stream,
	 * which must be parsable using DOM.
	 * @param resource XML resource to load bean definitions from
	 * @param parentBeanFactory parent bean factory
	 * @throws BeansException in case of loading or parsing errors
	 */
	public XmlBeanFactory(Resource resource, BeanFactory parentBeanFactory) throws BeansException {
		// 调用父类的构造函数, 进行一个 重要的处理
		/**
		 * 在 AbstractAutowireCapableBeanFactory.class构造函数中有如下设置
		 * 		ignoreDependencyInterface(BeanNameWare.class)
		 * 		ignoreDependencyInterface(BeanFactoryAwrae.class)
		 * 		ignoreDependencyInterface(BeanClassLoaderAware.class)
		 *
		 * 	为什么会有上面的 ignore 接口初始化呢?
		 * 	当 A 中有属性B时, 在 Spring 获取A的Bean的时候, 如果属性 B还没有初始化, 则 Spring 会自动初始化B(这是Spring 的一个重要特性)
		 * 	但是, 某些情况下, B 不会被初始化, 其中一种情况就是 B 实现了 BeanNameAware 接口
		 * 	ignoreDependencyInterface 中注释说明了, 忽略给定依赖接口的自动装配功能, 典型的是通过其他凡是解析 ApplicationContext
		 * 	比如: BeanFactory 通过 BeanFactoryAware 进行注入, 或者 ApplicationContext 通过 ApplicationContextAware 进行注入
		 */
		super(parentBeanFactory);						// 此处调用 AbstractAutowireCapableBeanFactory.class
		this.reader.loadBeanDefinitions(resource);		// 整个资源加载的核心
	}

}
