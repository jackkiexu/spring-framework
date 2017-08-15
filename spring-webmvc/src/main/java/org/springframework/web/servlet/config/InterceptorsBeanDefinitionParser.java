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

package org.springframework.web.servlet.config;

import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser} that parses a
 * {@code interceptors} element to register a set of {@link MappedInterceptor} definitions.
 *
 * @author Keith Donald
 * @since 3.0
 *
 * 参考资料
 * http://www.cnblogs.com/question-sky/p/7077788.html
 *
 * 解析 mvc:interceptors 节点并注册成 MappedInterceptors Definition 集合
 *
 * demo
 *     <!-- 拦截器设置 -->
		<mvc:interceptors>
			<mvc:interceptor>
				<mvc:mapping path="/**"/>
				<!-- 静态资源不拦截 -->
				<mvc:exclude-mapping path="/mobile/**"/>
				<mvc:exclude-mapping path="/pc/**"/>
				<!-- 主页不拦截 -->

				<!-- 特殊user资源获取不拦截 -->
				<bean class="com.du.wx.interceptor.UserInterceptor" />
			</mvc:interceptor>
		</mvc:interceptors>
 */
class InterceptorsBeanDefinitionParser implements BeanDefinitionParser {

	@Override
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		// CompositeComponentDefinition 表示其可以装载多个 ComponentDefinition
		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
		parserContext.pushContainingComponent(compDefinition);

		// 允许 mvc:interceptors 拥有 path-mather 属性 ,表示路径匹配解析器
		RuntimeBeanReference pathMatcherRef = null;
		if (element.hasAttribute("path-matcher")) {
			pathMatcherRef = new RuntimeBeanReference(element.getAttribute("path-matcher"));
		}
		// 查询 mvc: interceptors 节点下的 bean/ref/interceptor 标签
		List<Element> interceptors = DomUtils.getChildElementsByTagName(element, "bean", "ref", "interceptor");
		for (Element interceptor : interceptors) {
			// 采用 MappedInterceptor 作为 beanClass
			RootBeanDefinition mappedInterceptorDef = new RootBeanDefinition(MappedInterceptor.class);
			mappedInterceptorDef.setSource(parserContext.extractSource(interceptor));
			mappedInterceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

			ManagedList<String> includePatterns = null;
			ManagedList<String> excludePatterns = null;
			Object interceptorBean;
			// 解析 mvc:interceptor 节点
			if ("interceptor".equals(interceptor.getLocalName())) {
				// 解析 mvc:mapping 节点中的path属性, 保存里面的拦截器路径集合
				includePatterns = getIncludePatterns(interceptor, "mapping");
				// 解析 mvc:exclude-mapping 节点中的 path属性, 保存里面的放行路径集合
				excludePatterns = getIncludePatterns(interceptor, "exclude-mapping");
				// 解析 bean 标签/ref 标签, 并封装成 beanDefinition
				Element beanElem = DomUtils.getChildElementsByTagName(interceptor, "bean", "ref").get(0);
				interceptorBean = parserContext.getDelegate().parsePropertySubElement(beanElem, null);
			}
			else {
				// 解析 bean 标签/ ref 标签, 并封装成 beanDefinition
				interceptorBean = parserContext.getDelegate().parsePropertySubElement(interceptor, null);
			}
			// MappedInterceptor 类的构造函数可接受三个参数
			mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(0, includePatterns);
			mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(1, excludePatterns);
			mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(2, interceptorBean);

			// 为 MappedInterceptor添加 pathMatcher 属性
			if (pathMatcherRef != null) {
				mappedInterceptorDef.getPropertyValues().add("pathMatcher", pathMatcherRef);
			}
			// 保存到 spring 的 bean 工厂中
			String beanName = parserContext.getReaderContext().registerWithGeneratedName(mappedInterceptorDef);
			parserContext.registerComponent(new BeanComponentDefinition(mappedInterceptorDef, beanName));
		}

		parserContext.popAndRegisterContainingComponent();
		return null;
	}

	private ManagedList<String> getIncludePatterns(Element interceptor, String elementName) {
		List<Element> paths = DomUtils.getChildElementsByTagName(interceptor, elementName);
		ManagedList<String> patterns = new ManagedList<String>(paths.size());
		for (Element path : paths) {
			patterns.add(path.getAttribute("path"));
		}
		return patterns;
	}

}
