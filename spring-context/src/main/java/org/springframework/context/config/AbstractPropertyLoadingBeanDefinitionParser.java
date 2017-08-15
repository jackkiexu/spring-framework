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

package org.springframework.context.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;

/**
 * Abstract parser for &lt;context:property-.../&gt; elements.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Dave Syer
 * @since 2.5.2
 */
abstract class AbstractPropertyLoadingBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	@Override
	protected boolean shouldGenerateId() {
		return true;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		// 获取 location 属性, 支持 , 分隔, 表明指定读取配置文件的路径
		String location = element.getAttribute("location");
		if (StringUtils.hasLength(location)) {
			location = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(location);
			String[] locations = StringUtils.commaDelimitedListToStringArray(location);
			builder.addPropertyValue("locations", locations);
		}
		// 获取 properties-ref 属性, 设置 properties 文件的引用名
		String propertiesRef = element.getAttribute("properties-ref");
		if (StringUtils.hasLength(propertiesRef)) {
			builder.addPropertyReference("properties", propertiesRef);
		}
		// 获取 file-encoding 属性, 文件解析字符编码集
		String fileEncoding = element.getAttribute("file-encoding");
		if (StringUtils.hasLength(fileEncoding)) {
			builder.addPropertyValue("fileEncoding", fileEncoding);
		}
		// 获取 order 属性, 用于排序
		String order = element.getAttribute("order");
		if (StringUtils.hasLength(order)) {
			builder.addPropertyValue("order", Integer.valueOf(order));
		}
		// 获取 ignore-resource-not-found 属性, 默认 false, 表明找不到资源是否往外抛异常
		builder.addPropertyValue("ignoreResourceNotFound",
				Boolean.valueOf(element.getAttribute("ignore-resource-not-found")));
		// 获取 local-override 属性, 默认为 false, true 表明先使用文件中的属性, 找不到在使用环境变量中的属性
		builder.addPropertyValue("localOverride",
				Boolean.valueOf(element.getAttribute("local-override")));

		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
	}

}
