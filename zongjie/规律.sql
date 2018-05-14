------------------------------------ 常见规律 + 设计模式 --------------------------------------------
1. 通常 Spring 里面做一件事情 会分两个方法(比如 resolve, doResolve 方法, 而 doResolve 才是正真做事的方法)
2. 建造者模式 通常类名以 Builder 结尾
  2.1 BeanDefinitionBuilder
      通过一个类来直接构建 BeanDefinition
  2.2 EmbeddedDatabaseBuilder
      构建 Database
  2.3 BeanFactoryAspectJAdvisorsBuilder
      构建被 Aspect 注解修饰的  Advisor
  2.4 Jackson2ObjectMapperBuilder
      构建一个 ObjectMapper 对象 + 设置对象属性
  2.5 UriComponentsBuilder
      构建 URI 的建造(设置 scheme, host, path, queryParam)
  2.6 MvcUriComponentsBuilder
      与 UriComponentsBuilder 相似, 只不过是通过 Class, Method 上获取对应 URI 的信息
  2.7 ServletUriComponentsBuilder
      与 UriComponentsBuilder 相似, 只不过是通过 HttpServletRequest 上获取对应 URI 的信息
  2.8 MessageBuilder
      消息的建造者, 创建 payload, headers

3. 以 Support 为尾缀命名的类
  3.1 PropertyEditorRegistrySupport: 属性编辑注册器的支持

4. 以 Registry 为尾缀命名的类
  4.1 PropertyEditorRegistry: 属性编辑器对应的注册器

5. 以 Config 为尾缀命名的类

6. 以 Delegate 为尾缀命名的类

7. 以 Holder 为尾缀命名的类

8. 适配器模式, 含有 Adapter 关键字
    正真的适配器: RequestMappingHandlerAdapter
    接口适配器:
        CallableProcessingInterceptorAdapter
        HandlerInterceptorAdapter
    对象适配器
        FormatterPropertyEditorAdapter

9. 解释器模式, 含有 Parser 关键字

10. 装饰器模式, 含有 Decorator 关键字

11. 代理模式 jdk, cglib + 抽闲工厂

12. 组合模式, 含有 Composite 关键字

13. 策略模式, 含有 Strategy 关键字

14. 模板模式, 多个模板方法 + 策略模式 + 工厂模式 综合使用

15. 原型模式

16. 单例模式

17. 观察者模式, 含有 Listener 关键字

18. 访问者模式, 含有 Vister 关键字

19. 面门模式, 含有 Facade 关键字

20. 命令模式,
    -- 完成一个 Http 请求

21. Handler 结尾的类

------------------------------------ Spring 里面重要概念 --------------------------------------------
1. FactoryBean
2. 桥接方法
3. Spring 里面的 3种数据转换 (PatternEditor, Converter, HandlerMethodArgumentResolver)

------------------------------------ Spring 里面使用的技术 --------------------------------------------
1. @Aspect 注解
2. ASM 获取方法的参数名

------------------------------------ Spring 提供出来的技术 --------------------------------------------
0. PathMatchingResourcePatternResolver
1. ReflectionUtils
2. AnnotationUtils:
3. AnnotationAwareOrderComparator
4. ResolvableType: 获取范型值
5. MethodIntrospector: 通过 MethodFilter 选择合适的方法  或通过 ReflectionUtils.doWithMethods
6. CachedIntrospectionResults: 通过 内省 获取 Bean 里面的所有属性
7. LruCache: 通过 LinkedHashMap 实现 LRU cache
8. AnnotatedElementUtils: 对 class, method 中注解的处理
9. OrderUtils: 针对 Ordered 的操作
10. Proxy.isProxyClass 是否是动态代理类

------------------------------------ Spring IOC ----------------------------------------------------
1. 什么是 Spring IOC
2. 为什么需要 IOC
3. Resource 的架构
3. BeanFactory, ApplicationContext(1. 自己手动注入Bean, 2. 查找对应配置文件, 3. 扫描配置文件, 4. 扫描对应类)
4.

------------------------------------ Spring AOP ----------------------------------------------------
1. aop 基本概念 advisor, advice, pointcut, before, return, afterreturn, around
2. 第一代 AOP ProxyFactory
3. 第二代 AOP ProxyFactoryBean
4. 第三代 AOP 通过 Spring scheme
5. 第四代 AOP 通过 @AspectJ 注解

------------------------------------ Spring tx ------------------------------------------------------
1. Mysql 事务的基本概念 + Spring 对其的扩充 + Spring 里面组件基本概念
2. 第一代事务 PlatformTransactionManager
3. 第二代事务 TransactionTemplate
4. 第三代事务 通过 Spring Scheme
5. 第四代事务 通过 @Transaction 标签 (TransactionInterceptor 拦截器)

------------------------------------ Spring MVC ----------------------------------------------------
1. 主要构建 DispatcherServlet, HandlerMapping, HandlerAdapter, HandlerMethod...
2. 第一代 Spring MVC 处理流程(主要还是在 HandlerMapping, HandlerAdapter 的区别上)
3. 第二代 Spring MVC

------------------------------------ Spring 常用注解 ----------------------------------------------------
1. @Autowire
  AutowiredAnnotationBeanPostProcessor
2. @Resource
  CommonAnnotationBeanPostProcessor

3. @Controller

4. @Service

5. @Repository

6. @Component

------------------------------------ Spring XML 常用配置 ----------------------------------------------------
1. <aop:aspectj-autoproxy expose-proxy="true"/>
  aop 暴露代理对象到 ThreadLocal 中

2. <context:component-scan></>
  扫描指定目录, 加载Bean到容器中

3. <tx:annotation-driven />
  增加 注解式事务支持

4. <context:annotation-config />

5. <context:property-placeholder />
  解析配置文件(properties 文件)

6. <mvc: annotation-driven />


参考资料:
Spring BeanFactory 体系结构
  http://www.cnblogs.com/zrtqsk/p/4028453.html

Spring 技术内幕分析
  http://blog.csdn.net/chjttony/article/category/1239946

Spring IOC 启动过程
  http://www.cnblogs.com/ITtangtang/p/3978349.html

Spring 5 源码分析
  https://muyinchen.github.io/tags/Spring/

Spring 开涛
  http://jinnianshilongnian.iteye.com/blog/1492424

SpringMVC 源码解析:
  http://www.cnblogs.com/fangjian0423/tag/springmvc/
  http://www.cnblogs.com/question-sky/tag/SpringMVC/

Spring 开闭原则
  http://jinnianshilongnian.iteye.com/blog/1489787
  http://jinnianshilongnian.iteye.com/blog/1492424

Spring Document
  Spring 3.x 中文: http://shouce.jb51.net/spring/
  Spring 3.x 英文: https://docs.spring.io/spring/docs/3.0.x/reference/
  Spring 4.x 中文: http://spring.cndocs.ml/

枚举的写法:
  org.springframework.http.HttpMethod