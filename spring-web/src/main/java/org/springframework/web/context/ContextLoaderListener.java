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

package org.springframework.web.context;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * 父级启动类, 可用 ContextLoader 启动和 ContextCleanListener 来关闭 Spring 的根 web 应用上下文
 * Bootstrap listener to start up and shut down Spring's root {@link WebApplicationContext}.
 * Simply delegates to {@link ContextLoader} as well as to {@link ContextCleanupListener}.
 *
 * 这里给出提示, 如果需要用到自定义 log4j 的配置的话, 则 ContextListener 需要在 log4jConfigListener 之后
 * <p>This listener should be registered after {@link org.springframework.web.util.Log4jConfigListener}
 * in {@code web.xml}, if the latter is used.
 *
 * 从 Spring 3.1 之后, 注入根 web 应用上下文可通过 WebApplicationInitializer, 容器在启动会加载此接口, 但是
 * 有个要求是 容器的 Servlet 版本必须是 3.0+, 对  Tomcat来说必须是 7.0.15 版本以上
 * <p>As of Spring 3.1, {@code ContextLoaderListener} supports injecting the root web
 * application context via the {@link #ContextLoaderListener(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 17.02.2003
 * @see #setContextInitializers
 * @see org.springframework.web.WebApplicationInitializer
 * @see org.springframework.web.util.Log4jConfigListener
 */
// 参考资料 : http://www.cnblogs.com/question-sky/p/6683345.html

/**
 * 这里有个关键的接口是 ServletContextListener, 我们应该知道实现此接口的类会在应用启动的时候, 自动调用其接口方法
 * contextInitialized(ServletContextEvent event), 在关闭应用时则会调用其另外一个接口方法 contextDestroyed(ServletContextEvent event) 用来关闭 web 上下文信息
 *
 * 1. web.xml 的listner 节点具有先写先加载的特点, 类似于 Java 代码中的顺序执行
 * 2. Log4jConfigListener 类加载必须在 ContextLoaderListener 类之前
 * 3. ContextLoaderListener 启动 Spring 并生成 Spring 根  web 服务, 上下文则通过其父类 ContextLoader 来实现的, 其销毁也只会销毁具有
 *  	org.springframework 前缀的属性
 * 4. ServletContext 代表应用服务上线文, 其可以读取 <context-param> 级别的参数, 而 ServletConfig/FilterConfig 则会读取其相应
 * 		servlet节点, filter节点 下的 <init-param> 参数
 * 5. web.xml 中listner, servlet,filter 执行顺序为 listener > filter > servlet, 同类型的执行顺序为 listener 满足先写先加载, servlet, filter 则由
 * 		mapping 节点的先后顺序加载
 *
 */
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {

	/**
	 * Create a new {@code ContextLoaderListener} that will create a web application
	 * context based on the "contextClass" and "contextConfigLocation" servlet
	 * context-params. See {@link ContextLoader} superclass documentation for details on
	 * default values for each.
	 * <p>This constructor is typically used when declaring {@code ContextLoaderListener}
	 * as a {@code <listener>} within {@code web.xml}, where a no-arg constructor is
	 * required.
	 * <p>The created application context will be registered into the ServletContext under
	 * the attribute name {@link WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}
	 * and the Spring application context will be closed when the {@link #contextDestroyed}
	 * lifecycle method is invoked on this listener.
	 * @see ContextLoader
	 * @see #ContextLoaderListener(WebApplicationContext)
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener() {
	}

	/**
	 * Create a new {@code ContextLoaderListener} with the given application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based
	 * registration of listeners is possible through the {@link javax.servlet.ServletContext#addListener}
	 * API.
	 * <p>The context may or may not yet be {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#refresh() refreshed}. If it
	 * (a) is an implementation of {@link ConfigurableWebApplicationContext} and
	 * (b) has <strong>not</strong> already been refreshed (the recommended approach),
	 * then the following will occur:
	 * <ul>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #customizeContext} will be called</li>
	 * <li>Any {@link org.springframework.context.ApplicationContextInitializer ApplicationContextInitializer}s
	 * specified through the "contextInitializerClasses" init-param will be applied.</li>
	 * <li>{@link org.springframework.context.ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * <p>In any case, the given application context will be registered into the
	 * ServletContext under the attribute name {@link
	 * WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE} and the Spring
	 * application context will be closed when the {@link #contextDestroyed} lifecycle
	 * method is invoked on this listener.
	 * @param context the application context to manage
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener(WebApplicationContext context) {
		super(context);
	}


	/**
	 * Initialize the root web application context.
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {
		// 调用的是父类 ContextLoader的方法,  看出来这是启动的关键
		initWebApplicationContext(event.getServletContext());
	}


	/**
	 * Close the root web application context.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		// 与初始化相对
		closeWebApplicationContext(event.getServletContext());
		// 使用 ContextCleanupListener监听类来销毁 ServletContext的springwork属性信息
		ContextCleanupListener.cleanupAttributes(event.getServletContext());
	}

}
