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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.ReaderEventListener;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.Constants;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.Assert;
import org.springframework.util.xml.SimpleSaxErrorHandler;
import org.springframework.util.xml.XmlValidationModeDetector;

/**
 * Bean definition reader for XML bean definitions.
 * Delegates the actual XML document reading to an implementation
 * of the {@link BeanDefinitionDocumentReader} interface.
 *
 * <p>Typically applied to a
 * {@link org.springframework.beans.factory.support.DefaultListableBeanFactory}
 * or a {@link org.springframework.context.support.GenericApplicationContext}.
 *
 * <p>This class loads a DOM document and applies the BeanDefinitionDocumentReader to it.
 * The document reader will register each bean definition with the given bean factory,
 * talking to the latter's implementation of the
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry} interface.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @since 26.11.2003
 * @see #setDocumentReaderClass
 * @see BeanDefinitionDocumentReader
 * @see DefaultBeanDefinitionDocumentReader
 * @see BeanDefinitionRegistry
 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory
 * @see org.springframework.context.support.GenericApplicationContext
 *
 * 参考资料: http://www.cnblogs.com/VergiLyn/p/6130188.html
 *
 * 整个 XML 加载的核心代码
 */
public class XmlBeanDefinitionReader extends AbstractBeanDefinitionReader {

	/**
	 * Indicates that the validation should be disabled.
	 */
	public static final int VALIDATION_NONE = XmlValidationModeDetector.VALIDATION_NONE;

	/**
	 * Indicates that the validation mode should be detected automatically.
	 */
	public static final int VALIDATION_AUTO = XmlValidationModeDetector.VALIDATION_AUTO;

	/**
	 * Indicates that DTD validation should be used.
	 */
	public static final int VALIDATION_DTD = XmlValidationModeDetector.VALIDATION_DTD;

	/**
	 * Indicates that XSD validation should be used.
	 */
	public static final int VALIDATION_XSD = XmlValidationModeDetector.VALIDATION_XSD;


	/** Constants instance for this class */
	private static final Constants constants = new Constants(XmlBeanDefinitionReader.class);

	private int validationMode = VALIDATION_AUTO;

	private boolean namespaceAware = false;

	private Class<?> documentReaderClass = DefaultBeanDefinitionDocumentReader.class;

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	private ReaderEventListener eventListener = new EmptyReaderEventListener();

	private SourceExtractor sourceExtractor = new NullSourceExtractor();

	private NamespaceHandlerResolver namespaceHandlerResolver;
	// DocumentLoader 是一个接口, spring 唯一实现类是 DefaultDocumentLoader
	private DocumentLoader documentLoader = new DefaultDocumentLoader();

	private EntityResolver entityResolver;

	private ErrorHandler errorHandler = new SimpleSaxErrorHandler(logger);

	private final XmlValidationModeDetector validationModeDetector = new XmlValidationModeDetector();

	// 记录加载过的资源(PS: 这里使用了 ThreadLocal)
	private final ThreadLocal<Set<EncodedResource>> resourcesCurrentlyBeingLoaded =
			new NamedThreadLocal<Set<EncodedResource>>("XML bean definition resources currently being loaded");


	/**
	 * Create new XmlBeanDefinitionReader for the given bean factory.
	 * @param registry the BeanFactory to load bean definitions into,
	 * in the form of a BeanDefinitionRegistry
	 */
	public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
		super(registry);
	}


	/**
	 * Set whether to use XML validation. Default is {@code true}.
	 * <p>This method switches namespace awareness on if validation is turned off,
	 * in order to still process schema namespaces properly in such a scenario.
	 * @see #setValidationMode
	 * @see #setNamespaceAware
	 */
	public void setValidating(boolean validating) {
		this.validationMode = (validating ? VALIDATION_AUTO : VALIDATION_NONE);
		this.namespaceAware = !validating;
	}

	/**
	 * Set the validation mode to use by name. Defaults to {@link #VALIDATION_AUTO}.
	 * @see #setValidationMode
	 */
	public void setValidationModeName(String validationModeName) {
		setValidationMode(constants.asNumber(validationModeName).intValue());
	}

	/**
	 * Set the validation mode to use. Defaults to {@link #VALIDATION_AUTO}.
	 * <p>Note that this only activates or deactivates validation itself.
	 * If you are switching validation off for schema files, you might need to
	 * activate schema namespace support explicitly: see {@link #setNamespaceAware}.
	 */
	public void setValidationMode(int validationMode) {
		this.validationMode = validationMode;
	}

	/**
	 * Return the validation mode to use.
	 */
	public int getValidationMode() {
		return this.validationMode;
	}

	/**
	 * Set whether or not the XML parser should be XML namespace aware.
	 * Default is "false".
	 * <p>This is typically not needed when schema validation is active.
	 * However, without validation, this has to be switched to "true"
	 * in order to properly process schema namespaces.
	 */
	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

	/**
	 * Return whether or not the XML parser should be XML namespace aware.
	 */
	public boolean isNamespaceAware() {
		return this.namespaceAware;
	}

	/**
	 * Specify which {@link org.springframework.beans.factory.parsing.ProblemReporter} to use.
	 * <p>The default implementation is {@link org.springframework.beans.factory.parsing.FailFastProblemReporter}
	 * which exhibits fail fast behaviour. External tools can provide an alternative implementation
	 * that collates errors and warnings for display in the tool UI.
	 */
	public void setProblemReporter(ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Specify which {@link ReaderEventListener} to use.
	 * <p>The default implementation is EmptyReaderEventListener which discards every event notification.
	 * External tools can provide an alternative implementation to monitor the components being
	 * registered in the BeanFactory.
	 */
	public void setEventListener(ReaderEventListener eventListener) {
		this.eventListener = (eventListener != null ? eventListener : new EmptyReaderEventListener());
	}

	/**
	 * Specify the {@link SourceExtractor} to use.
	 * <p>The default implementation is {@link NullSourceExtractor} which simply returns {@code null}
	 * as the source object. This means that - during normal runtime execution -
	 * no additional source metadata is attached to the bean configuration metadata.
	 */
	public void setSourceExtractor(SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new NullSourceExtractor());
	}

	/**
	 * Specify the {@link NamespaceHandlerResolver} to use.
	 * <p>If none is specified, a default instance will be created through
	 * {@link #createDefaultNamespaceHandlerResolver()}.
	 */
	public void setNamespaceHandlerResolver(NamespaceHandlerResolver namespaceHandlerResolver) {
		this.namespaceHandlerResolver = namespaceHandlerResolver;
	}

	/**
	 * 可以自己实现 DocumentLoader, 然后通过该方法设置 documentLoader
	 * Specify the {@link DocumentLoader} to use.
	 * <p>The default implementation is {@link DefaultDocumentLoader}
	 * which loads {@link Document} instances using JAXP.
	 */
	public void setDocumentLoader(DocumentLoader documentLoader) {
		this.documentLoader = (documentLoader != null ? documentLoader : new DefaultDocumentLoader());
	}

	/**
	 * Set a SAX entity resolver to be used for parsing.
	 * <p>By default, {@link ResourceEntityResolver} will be used. Can be overridden
	 * for custom entity resolution, for example relative to some specific base path.
	 */
	public void setEntityResolver(EntityResolver entityResolver) {
		this.entityResolver = entityResolver;
	}

	/**
	 * Return the EntityResolver to use, building a default resolver
	 * if none specified.
	 * 参考资料
	 * http://www.infocool.net/kb/Java/201607/167680.html#自带命名空间标签的解析
	 *
	 * EntityResolver 是个什么概念呢? 我们定义的 XML 文件由引用相关的 DTD 和 XSD, 他们都是 一个 URL, 那么一般会从网络上下载这些 DTD 和 XSD, 但是
	 * 但网络不好的时候或者网络断网的时候就体验不好或者无法做到, 这里使用了 EntityResolver 解析本地 DTD 和 XSD(Schema)
	 * 这里有两种 EntityResolver: ResourceEntityResolver 和 DelegatingEntityResolver, 而实际上前者是后者的子类. 前者的作用是如果
	 * DelegatingEntityResolver 加载不到资源, 那么再使用已设置的 ResourceLoader 尝试加载资源
	 */
	protected EntityResolver getEntityResolver() {
		if (this.entityResolver == null) {
			// Determine default EntityResolver to use.
			ResourceLoader resourceLoader = getResourceLoader();
			if (resourceLoader != null) {
				this.entityResolver = new ResourceEntityResolver(resourceLoader);
			}
			else {
				this.entityResolver = new DelegatingEntityResolver(getBeanClassLoader());
			}
		}
		return this.entityResolver;
	}

	/**
	 * Set an implementation of the {@code org.xml.sax.ErrorHandler}
	 * interface for custom handling of XML parsing errors and warnings.
	 * <p>If not set, a default SimpleSaxErrorHandler is used that simply
	 * logs warnings using the logger instance of the view class,
	 * and rethrows errors to discontinue the XML transformation.
	 * @see SimpleSaxErrorHandler
	 */
	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	/**
	 * Specify the {@link BeanDefinitionDocumentReader} implementation to use,
	 * responsible for the actual reading of the XML bean definition document.
	 * <p>The default is {@link DefaultBeanDefinitionDocumentReader}.
	 * @param documentReaderClass the desired BeanDefinitionDocumentReader implementation class
	 */
	public void setDocumentReaderClass(Class<?> documentReaderClass) {
		if (documentReaderClass == null || !BeanDefinitionDocumentReader.class.isAssignableFrom(documentReaderClass)) {
			throw new IllegalArgumentException(
					"documentReaderClass must be an implementation of the BeanDefinitionDocumentReader interface");
		}
		this.documentReaderClass = documentReaderClass;
	}


	/**
	 * Load bean definitions from the specified XML file.
	 * @param resource the resource descriptor for the XML file
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 */
	// XmlBeanDefinitionReader 加载资源的入口方法
	@Override
	public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
		/**
		 * 1. 把 Resource 进一步封装为 EncodeResource
		 * 		原因: 考虑到 Resource 可能对 编码有要求, 所有 EncodedResource 可以指定 encoding编码, charset字符集
		 * 		EncodedResource 中的重要的方法是getReader(), 返回 InoutStreamReader.class
		 * 2. 再调用加载资源的方法
		 */
		return loadBeanDefinitions(new EncodedResource(resource));		// 将读入的XML资源进行特殊的编码处理
	}

	/**
	 * Load bean definitions from the specified XML file.
	 * @param encodedResource the resource descriptor for the XML file,
	 * allowing to specify an encoding to use for parsing the file
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 */
	// 这里是载入 XML 形式 Bean 定义资源文件的方法
	public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
		Assert.notNull(encodedResource, "EncodedResource must not be null");
		if (logger.isInfoEnabled()) {
			logger.info("Loading XML bean definitions from " + encodedResource.getResource());
		}
		// 通过类属性 resourcesCurrentlyBeingLoaded 记录已加载的资源
		Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
		if (currentResources == null) {
			currentResources = new HashSet<EncodedResource>(4);
			this.resourcesCurrentlyBeingLoaded.set(currentResources);
		}
		// 添加新资源到类属性, 最后 finally 中有 remove() 处理循环加载 exception
		if (!currentResources.add(encodedResource)) {
			// 循环加载 exception
			throw new BeanDefinitionStoreException(
					"Detected cyclic loading of " + encodedResource + " - check your import definitions!");
		}
		try {
			// 这里得到 XML 文件, 并得到 IO 的 InputSource 准备进行读取
			// 封装的 EncodedResource 先获取 Resource, 再通过 Resource 获取 InputStream
			// 将资源文件转为 InputStream 的 IO 流
			InputStream inputStream = encodedResource.getResource().getInputStream();
			try {
				// 从 InputStream 中得到XML的解析源
				// 把 InputStream 封装成 InputSource
				InputSource inputSource = new InputSource(inputStream);
				if (encodedResource.getEncoding() != null) {
					inputSource.setEncoding(encodedResource.getEncoding());
				}
				// doLoadbeanDefinitions 才是核心加载资源文件的方法
				return doLoadBeanDefinitions(inputSource, encodedResource.getResource());		// 这里是具体的读取过程
			}
			finally {
				inputStream.close();				// 关闭从 Resource 中得到的 IO 流
			}
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"IOException parsing XML document from " + encodedResource.getResource(), ex);
		}
		finally {
			currentResources.remove(encodedResource);
			if (currentResources.isEmpty()) {
				this.resourcesCurrentlyBeingLoaded.remove();
			}
		}
	}

	/**
	 * Load bean definitions from the specified XML file.
	 * @param inputSource the SAX InputSource to read from
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 */
	public int loadBeanDefinitions(InputSource inputSource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(inputSource, "resource loaded through SAX InputSource");
	}

	/**
	 * Load bean definitions from the specified XML file.
	 * @param inputSource the SAX InputSource to read from
	 * @param resourceDescription a description of the resource
	 * (can be {@code null} or empty)
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 */
	public int loadBeanDefinitions(InputSource inputSource, String resourceDescription)
			throws BeanDefinitionStoreException {

		return doLoadBeanDefinitions(inputSource, new DescriptiveResource(resourceDescription));
	}


	/**
	 * Actually load bean definitions from the specified XML file.
	 * @param inputSource the SAX InputSource to read from
	 * @param resource the resource descriptor for the XML file
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of loading or parsing errors
	 * @see #doLoadDocument
	 * @see #registerBeanDefinitions
	 */
	/**
	 * 具体的读取过程可以在 doLoadBeanDefinitions 方法中找到
	 * 这是从特定的 XML 文件中实际载入 BeanDefinition 的地方
	 */
	// 从特定 XML 文件中实际载入 Bean 定义资源的方法
	protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
			throws BeanDefinitionStoreException {
		try {
			/**
			 * 这里取得 XML 文件的 Document 对象, 这个解析过程是由 documentLoader 完成
			 * 的这个 documentLoader 是 DefaultDocumentLoader, 在定义 documentLoader 的地方创建
			 */
			// 将 XML 文件转换为 DOM 对象, 解析过程由 documentLoader 实现
			Document doc = doLoadDocument(inputSource, resource);
			/**
			 * 这里启动的是对 BeanDefinition 解析的详细过程, 这个解析会使用到 Spring 的 Bean
			 * 配置规则
			 */
			// 这里是启动对 Bean 定义解析的详细过程, 该解析过程会用到 Spring 的 Bean 配置规则
			return registerBeanDefinitions(doc, resource);
		}
		catch (BeanDefinitionStoreException ex) {
			throw ex;
		}
		catch (SAXParseException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex);
		}
		catch (SAXException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"XML document from " + resource + " is invalid", ex);
		}
		catch (ParserConfigurationException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Parser configuration exception parsing XML from " + resource, ex);
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"IOException parsing XML document from " + resource, ex);
		}
		catch (Throwable ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Unexpected exception parsing XML document from " + resource, ex);
		}
	}

	/**
	 * Actually load the specified document using the configured DocumentLoader.
	 * @param inputSource the SAX InputSource to read from
	 * @param resource the resource descriptor for the XML file
	 * @return the DOM Document
	 * @throws Exception when thrown from the DocumentLoader
	 * @see #setDocumentLoader
	 * @see DocumentLoader#loadDocument
	 *
	 * 参考资料
	 * https://duqingfeng.me/2017/07/19/spring%E6%BA%90%E7%A0%81%E2%80%94%E2%80%94%E4%BB%8Exml%E5%88%B0org.w3c.dom.Document/
	 * http://blog.csdn.net/disiwei1012/article/details/75209030
	 *
	 * 常见的 XML 解析方法有 DOM, SAX, dom4j 等, 那么 Spring 采用的是哪一种方式?
	 * spring 中采用的是 Dom 方法, 所要做的一切就是得到  org.w3c.dom.Document
	 *
	 * loaDocument 传递的参数有
	 * 1. EntityResolver: 指定XML验证方法,  实现先从本地获取 XSD 或者 DTD 文件
	 * 2. errorHandler: 解析错误处理类
	 * 3. ValidationMode: 验证方式 XSD, DTD
	 * 4. isNamespaceAware: 是否支持命名空间
	 *
	 * inputSource: 就是通过Resource.getInputStream() 再用 InputStream 构造 InputSource
	 * getEntityResolver: 得到一个实体解析器(org.xml.sax.EntityResolver), 可以通过 AbstractBeanDefinitionReader.setResourceLoader() 设置
	 * EntityResolver: DelegatingEntityResolver 实现对文档验证实体的转换 ,可以自动实现转换 http.. 形式的 DTD 和 XSD 文件到本地, 对无互联网环境的支持
	 * ErrorHandler: SimpleSaxErrorHandler 进行错误回调处理
	 * getValidationModeForResource: 判断 XML 文档内定义的验证类型(DTD和 XSD), 其实就是逐行判断文档里面是否有 "DOCTYPE", 有则是 DTD 否则是 XSD
	 * 		获取对 XML 文件验证模式
	 *
	 * 利用 spring 核心的 DocumentLoader 加载XMl, 得到对应 org.w3c.dom.Document
	 *
	 */
	protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
		return this.documentLoader.loadDocument(inputSource, getEntityResolver(), this.errorHandler,
				getValidationModeForResource(resource), isNamespaceAware());
	}


	/**
	 * Gets the validation mode for the specified {@link Resource}. If no explicit
	 * validation mode has been configured then the validation mode is
	 * {@link #detectValidationMode detected}.
	 * <p>Override this method if you would like full control over the validation
	 * mode, even when something other than {@link #VALIDATION_AUTO} was set.
	 *
	 * 获取指定的 Resource 的验证模式, 如果没有配置明确的验证模式, 则使用自动检测
	 */
	protected int getValidationModeForResource(Resource resource) {
		// 如果手动指定了 验证模式, 则使用指定的
		// 可在 XMlBeanDefinitionReader.setValidationMode() 方法设置
		int validationModeToUse = getValidationMode();
		if (validationModeToUse != VALIDATION_AUTO) {
			return validationModeToUse;
		}	// 判断 XML 文档内定义的验证类型(DTD和 XSD), 其实就是逐行判断文档里面是否有 "DOCTYPE", 有则是 DTD 否则是 XSD
		// 未指定, 则使用自动检测
		int detectedMode = detectValidationMode(resource);
		if (detectedMode != VALIDATION_AUTO) {
			return detectedMode;
		}
		// Hmm, we didn't get a clear indication... Let's assume XSD,
		// since apparently no DTD declaration has been found up until
		// detection stopped (before finding the document's root tag).
		return VALIDATION_XSD;
	}

	/**
	 * Detects which kind of validation to perform on the XML file identified
	 * by the supplied {@link Resource}. If the file has a {@code DOCTYPE}
	 * definition then DTD validation is used otherwise XSD validation is assumed.
	 * <p>Override this method if you would like to customize resolution
	 * of the {@link #VALIDATION_AUTO} mode.
	 */
	// 判断 XML 文档内定义的验证类型(DTD和 XSD), 其实就是逐行判断文档里面是否有 "DOCTYPE", 有则是 DTD 否则是 XSD
	protected int detectValidationMode(Resource resource) {
		if (resource.isOpen()) {
			throw new BeanDefinitionStoreException(
					"Passed-in Resource [" + resource + "] contains an open stream: " +
					"cannot determine validation mode automatically. Either pass in a Resource " +
					"that is able to create fresh streams, or explicitly specify the validationMode " +
					"on your XmlBeanDefinitionReader instance.");
		}

		InputStream inputStream;
		try {
			inputStream = resource.getInputStream();
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"Unable to determine validation mode for [" + resource + "]: cannot open InputStream. " +
					"Did you attempt to load directly from a SAX InputSource without specifying the " +
					"validationMode on your XmlBeanDefinitionReader instance?", ex);
		}

		try {	// 判断 XML 文档内定义的验证类型(DTD和 XSD), 其实就是逐行判断文档里面是否有 "DOCTYPE", 有则是 DTD 否则是 XSD
			return this.validationModeDetector.detectValidationMode(inputStream);
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("Unable to determine validation mode for [" +
					resource + "]: an error occurred whilst reading from the InputStream.", ex);
		}
	}

	/**
	 * Register the bean definitions contained in the given DOM document.
	 * Called by {@code loadBeanDefinitions}.
	 * <p>Creates a new instance of the parser class and invokes
	 * {@code registerBeanDefinitions} on it.
	 * @param doc the DOM document
	 * @param resource the resource descriptor (for context information)
	 * @return the number of bean definitions found
	 * @throws BeanDefinitionStoreException in case of parsing errors
	 * @see #loadBeanDefinitions
	 * @see #setDocumentReaderClass
	 * @see BeanDefinitionDocumentReader#registerBeanDefinitions
	 *
	 * 参考资料: http://acooly.iteye.com/blog/1707354
	 * 可以开始真正的解析了. 对 Document 的解析分为两种情况, 一种是默认的名字空间 beans (http://www.springframework.org/schema/beans, 无前缀的配置如: bean)
	 * 和其他命名空间的节点的解析(有前缀, 如 context:property-placeholder)
	 *
	 * 无前缀的 beans 默认命名空间节点: 采用 BeanDefinitionParserDelegate(解析工具类) 完成节点的解析
	 * 有前缀的其他命名空间节点: 使用解析框架完成解析, 具体逻辑为首先使用 namespace的 SystemId(就是URL 全路径) 通过 NamespacehandlerResolver 找到对应 Namespacehandler,
	 * 		然后通过具体的 NamespaceHandler 的 parse 方法解析节点, 在Namespacehandler 内部通过节点名称找到对应 BeanDefinitionParser 解析器完成节点的解析并完成 beanDefinition
	 */
	// 按照 Spring 的 Bean 语义要求将 Bean 定义资源解析并转换为容器内部的数据结构
	public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
		// 这里得到 BeanDefinitionDocumentReader 来对 XML 的 BeanDefinition 解析解析
		// 得到  BeanDefinitionDocumentReader 来对 XML 格式的 BeanDefinition解析
		// 实际实现类是: DefaultBeanDefinitionDocumentReader
		BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
		// 获取容器中注册的 bean 的数量
		// 此处的 getRegistry() 方法返回的实例为 DefaultListableBeanFactory 类型
		// 获取 BeanFactory 已经注册的 BeanDefinition 数量
		int countBefore = getRegistry().getBeanDefinitionCount();
		// 具体的解析过程在 registerBeanDefinitions 中完成
		// 解析过程入口, 这里使用了委派模式, BeanDefinitionDocumentReader 只是一个接口, 具体的解析实现过程由实现类 DefaultbeanDefinitionDocumentReader 完成
		// 调用 DefaultBeanDefinitionDocumentReader.registerBeanDefinition 方法
		// 核心方法: 通过 BeanDefinitionDocumentReader 注册 doc 中定义的 bean 到 BeanFactory 中
		documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
		// 统计解析的 Bean 数量
		// 返回 本次注册的 BeanDefinition 的数量 = 目前 BeanFactory 中的总数 - countBefore
		return getRegistry().getBeanDefinitionCount() - countBefore;
	}

	/**
	 * Create the {@link BeanDefinitionDocumentReader} to use for actually
	 * reading bean definitions from an XML document.
	 * <p>The default implementation instantiates the specified "documentReaderClass".
	 * @see #setDocumentReaderClass
	 * 创建 BeanDinitionDocumentReader 用于实际从 XML 文档中读取 Bean 定义
	 * 默认是 DefaultBeanDefinitionDocumentReader
	 * 可通过 XmlBeanDefinitionReader.setDocumentReaderClass(...) 设置
	 */
	protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
		return BeanDefinitionDocumentReader.class.cast(BeanUtils.instantiateClass(this.documentReaderClass));
	}

	/**
	 * Create the {@link XmlReaderContext} to pass over to the document reader.
	 */
	// 创建 beanDefinitionDocumentReader 对象, 解析 Docuement
	public XmlReaderContext createReaderContext(Resource resource) {
		return new XmlReaderContext(resource, this.problemReporter, this.eventListener,
				this.sourceExtractor, this, getNamespaceHandlerResolver());
	}

	/**
	 * Lazily create a default NamespaceHandlerResolver, if not set before.
	 * @see #createDefaultNamespaceHandlerResolver()
	 */
	public NamespaceHandlerResolver getNamespaceHandlerResolver() {
		if (this.namespaceHandlerResolver == null) {
			this.namespaceHandlerResolver = createDefaultNamespaceHandlerResolver();
		}
		return this.namespaceHandlerResolver;
	}

	/**
	 * Create the default implementation of {@link NamespaceHandlerResolver} used if none is specified.
	 * Default implementation returns an instance of {@link DefaultNamespaceHandlerResolver}.
	 */
	protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
		return new DefaultNamespaceHandlerResolver(getResourceLoader().getClassLoader());
	}

}
