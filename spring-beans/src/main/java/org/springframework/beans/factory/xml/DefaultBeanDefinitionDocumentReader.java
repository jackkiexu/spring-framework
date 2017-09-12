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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 *
 * 参考资料: http://www.cnblogs.com/VergiLyn/p/6130188.html
 *
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	private XmlReaderContext readerContext;

	private BeanDefinitionParserDelegate delegate;


	/**
	 * 注册 bean definition 的核心, 该方法提取 Document 中的 org.w3c.dom.Element 及设置 XmlReaderContext readerContext = XX
	 * 然后把提取的 Element 传入核心的 doRegisterBeanDefinition(element) 进一步处理
	 *
	 * 根据 XSD / DTD 解析除 Bean Definition
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * 打开一个 DOM 文档, 然后初始化在默认的指定的 <bean/> 中, 最后解析包含的 bean definition
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	// 根据 Spring DTD 对 Bean 的定义规则解析 bean 定义的 Document
	// 解析 bean 定义
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		// 获取 XML 描述符
		this.readerContext = readerContext;
		logger.debug("Loading bean definitions");
		// 获取 Document 的根元素
		// 此处的 root 节点一般是 <beans> 节点
		Element root = doc.getDocumentElement();
		// 具体解析的方法
		// 调用的是 DefaultBeanDefinitionDocumentReader#doRegisterBeanDefinitions
		doRegisterBeanDefinitions(root);
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor} to pull the
	 * source metadata from the supplied {@link Element}.
	 */
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * 注册的核心方法, 利用了模板方法
	 * 注册每个 bean definition 从给定的元素 <bean/> 中
	 * 如果想在 XML 解析前后对 Element 元素做一些处理
	 * 	则在 DefaultBeanDefinitionDocumentreader 的子类重写 preProcessXml(...) postProcessXml(..) 即可
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		// 具体的解析过程由 BeanDefinitionParserDelegate 实现
		// BeanDefinitionParserDelegate中定义了 Spring Bean 定义 XML 文件的各种元素
		BeanDefinitionParserDelegate parent = this.delegate;

		// 创建 BeanDefinitionParserDelegate, 用于完成正真的解析过程
		// 读取 <Beans> 标签中的 default-* 属性
		// 创建解析的委托处理工具类
		this.delegate = createDelegate(getReaderContext(), root, parent);

		if (this.delegate.isDefaultNamespace(root)) {
			// 此处针对 <Beans> 的节点属性 profile 进行操作
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isInfoEnabled()) {
						logger.info("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}
		// 在解析 Bean 定义之前, 进行自定义的解析, 增强解析过程的可扩展性
		// 预处理 bean xml配置文件中的自定义标签, 默认是空的
		// 解析前置处理, 这里是空实现 留给子类实现
		preProcessXml(root);
		// 解析 Bean.xml 配置文件
		// 从 Document 的根元素开始进行 bean 定义的 Document 对象
		// 解析整个文档, 轮询各个子节点分别解析,
		parseBeanDefinitions(root, this.delegate);
		// 在解析 Bean 定义之后, 进行自定义的解析, 增加解析过程的可扩展性
		// 处理 bean xml 配置文件中的自定义标签, 默认是空的
		// 解析后置处理, 也是空实现 留给子类实现
		postProcessXml(root);

		this.delegate = parent;
	}

	// 创建 BeanDefinitionParserDelegate 用于解析 默认标签
	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 */
	// 正如 注解所说的, 解析 import 标签, alias 标签, bean 标签, 和自定义标签
	// 使用 Spring 的 Bean 规则从 Document 的根元素开始进行Bean定义的 Document 对象
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 对 Beans 处理
		// Bean 定义额 Document 对象使用了 Spring 默认的 XML 命名空间
		// 检查 <beans> 根标签的命名空间是否为空或者是  http://www.springframework.org/schema/beans
		if (delegate.isDefaultNamespace(root)) {
			// 获取 Bean 定义的 Document 对象根元素的所有子节点
			// 遍历旗下的子节点
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				// 获取  Document 节点是 XML 元素节点
				if (node instanceof Element) {
					Element ele = (Element) node;
					//	Bean 定义的Document的元素节点使用的是 Spring 默认的 XML 命名空间
					// 每个子节点都有自己的命名空间
					// 若果是默认命名空间(beans), 则直接解析 比如 <bean id="b1" class="..."></bean>
					if (delegate.isDefaultNamespace(ele)) {
						// 使用 Spring 的 Bean 规则解析元素节点
						// 解析 import 标签, alias 标签, bean 标签, 内置 <beans> 标签
						parseDefaultElement(ele, delegate);
					}
					else {
						// 没有使用 Spring 默认的 XML 命名空间, 则使用用户自定义的解析规则解析元素节点
						// 解析自定义标签, 例如 <context:component-scan basePackage="" />
						// 如果是非默认空间, 则使用解析框架完成解析
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			// Document 的根节点没有使用 Spring 默认的命名空间, 则使用用户自定义的
			// 解析规则解析 Document 根节点
			delegate.parseCustomElement(root);
		}
	}

	// 使用 Spring 的 Bean 规则解析 Document 元素节点
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 如果元素节点是 <Import> 导入元素, 则进行导入解析
		// import 标签解析
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		// 如果元素节点是 <Alias> 别名元素, 进行别名解析
		// alias 标签解析
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		// 元素节点既不是导入元素, 也不是别名元素, 即普通的 <Bean> 元素,
		// 按照 Spring 的bean规则解析元素
		// 标签解析
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			// 递归
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 *
	 * Demo
	 *
	 * <beans>
	 *     <import resource="redis.xml"></import>
	 *     <import resource="db.xml"></import>
	 * </beans>
	 */
	// 解析 <Import> 导入元素, 从给定的导入路径加载 Bean定义资源到 Spring Ioc 容器中
	protected void importBeanDefinitionResource(Element ele) {
		// 获取 给定的导入元素的 location属性
		// 获取 <import> 标签的 resource 属性值
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		// 如果导入额元素的 location 属性值为空, 则没有导入任何资源, 直接返回
		// resource 不允许为空
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}
		// 使用系统变量值解析 location 属性值 如 ${user.dir}
		// Resolve system properties: e.g. "${user.dir}"
		// 这里强调一下, 对于 <import> 标签, 其会从 System.getProperties() 和 System.getenv() 中属性获取, 优先 System.getProperties()
		// 即优先系统变量, 环境变量次子
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<Resource>(4);

		// 标识给定的导入元素的 location 是否是绝对路径
		// Discover whether the location is an absolute or relative URI
		// Resource 属性支持  URL 模式, 找到相应的资源并进行加载
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
			// 给定的导入元素的 location 不是绝对路径
		}

		// 如果是绝对 URI 则直接根据 地址加载对应的配置文件
		// 给定的导入元素的 location 是绝对路径
		// Absolute or relative?
		if (absoluteLocation) {
			try {
				// 使用资源读入器加载给定路径的 Bean 定义资源
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// 如果是相对路径, 则根据相对地址计算出绝对地址
			// No URL -> considering resource location as relative to the current file.
			// 给定的导入元素的 location 是相对路径
			try {
				int importCount;
				/**
				 * Resource 存在多个子实现类, 如 VfsResource, FileSystemResource等
				 * 而每个 resource 的 createRelative 方式实现都不一样, 所以这里先使用子类的方法尝试解析
				 */
				// 相对路径, 相对于当前文件
				// 将给定导入元素的 location 封装为相对路径资源
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				// 封装的相对路径资源存在
				if (relativeResource.exists()) {		// 下面又是 解析 xml 里面的描述信息, 来加载 Bean
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				// 封装的相对路径资源不存在
				else {	// 如果解析不成功, 则使用默认的解析器 ResourcePatternResolver 进行解析
					// 获取 Spring Ioc 容器资源读入器的基本路径
					String baseLocation = getReaderContext().getResource().getURL().toString();
					// 根据 Spring Ioc 容器资源读入器的基本路径加载给定导入路径的资源
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]",
						ele, ex);
			}
		}
		// 解析后进行监听器激活处理
		Resource[] actResArray = actualResources.toArray(new Resource[actualResources.size()]);
		// 在解析完 <Import> 元素之后, 发送容器导入其他资源处理完成事件
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	// 解析 <Alias> 别名元素, 为 Bean 向 Spring Ioc 容器注册别名
	protected void processAliasRegistration(Element ele) {
		// 这里确保 <alias> 标签的 name alias 值不为空
		// 获取 <Alias> 别名元素中的 name 的属性值
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// 获取 <Alias> 别名元素中 alias 的属性值
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		// <Alias> 别名元素的 name 属性值为空
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		// <Alias> 别名元素的 alias 属性值为空
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				// 全局设置, 查阅后其会保存到 DefaultListableFactory 的父级类 SimpleAliasRegistry#aliasMap 中
				// 向容器的资源读入器注册别名
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			// 解析完 <Alias> 元素之后, 发送容器别名处理完成事件
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 *
	 * 处理给定的 bean 元素, 并且 解析 bean definition 和调用 beanDefinitionregistry.registryBeanDefinition(...) 方法相应的 Map 中
	 */
	// <bean> 标签的解析处理
	// 解析 Bean 定义资源 Document 对象的普通元素
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		/**
		 * BeanDefinitionHolder 是 BeanDefinition 对象的封装类, 封装了 BeanDefinition, Bean的名字和别名,
		 * 用它来完成 Ioc 容器注册. 得到这个 BeanDefinitionHolder 就意味着 BeanDefinition 是通过
		 * BeanDefinitionParserDelegate 对 XML 元素的信息 按照 Spring 的 Bean 规则进行解析得到
		 */
		/**
		 * 利用 BeanDefinitionParserDelegate.parseBeanDefinitionElement(Element e)
		 * 解析给定的 bean 元素信息为, BeanDefinitionHolder, 这其中就包含了 id, class, alias, name 等属性
		 */
		// BeanDefinitionHolder 是对 BeanDefinition 的封装, 即 Bean 定义的封装类
		// 对 Document 对象中 <Bean> 元素的解析由 BeanDefinitionParserDelegate实现,
		// 初始化 bean 标签的相应内容
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			// 若默认标签的子节点下游 自定义属性, 还需要再次对自定义标签进行解析
			// 对 bean 标签中的属性和子标签, 进行相应的具体解析, 例如 property, constructor-arhs
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// 这里是向 Ioc 容器注册解析得到 BeanDefinition 的地方
				// 调用 BeanDefinitionRegistry.registerBeanDefinition(...) 放到相应的 Map 中
				// 向 Spring Ioc 容器注册解析得到 Bean 定义, 这是 Bean 定义向 Ioc 容器注册的入口
				// Register the final decorated instance.
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// 向 BeanDefinition 向 Ioc 容器 注册完成以后, 发送消息
			// 发送注册事件
			// 通知相关的监听器, 这个 Bean 已经注册完成
			// 在完成向 Spring Ioc 容器注册解析得到的 bean 定义之后, 发送注册事件
			// Send registration event.
			// 目前尚无实现
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
