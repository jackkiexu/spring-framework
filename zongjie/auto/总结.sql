1. 常用注解
  1.1 @Configuration
  1.2 @Import
  1.3 @@Bean
  1.4 @Configuration:
      本身 Configuration 也是被 @Component 注解修饰, 同样会被注入到 Spring 容器里面
      处理器: ConfigurationClassPostProcessor
  1.5 @Autowired: AutowiredAnnotationBeanPostProcessor
  1.6 @Value: AutowiredAnnotationBeanPostProcessor
  1.7 @Required: RequiredAnnotationBeanPostProcessor
  1.8 @Resource: CommonAnnotationBeanPostProcessor
  1.9 @PersistenceContext: PersistenceAnnotationBeanPostProcessor
  2.0 @ComponentScan: ComponentScanAnnotationParser

2. 重要组件
  2.1 ConfigurationClassBeanDefinitionReader: 解析 @Configuration 注解的类, 并且生成 BeanDefinition
  2.2 ConfigurationClassParser: 被 @Configuration 注解修饰的类解析器
  2.3 ConfigurationClassPostProcessor: BeanFactory 后置处理器
  2.4 ConfigurationClass: 代表被 @Configuration 修饰的 class
  2.5 BeanMethod: 代表被 @Configuration 修饰的 class 中 被 @Bean 注解修饰的方法
  2.6 ClassPathBeanDefinitionScanner:
  2.7 ComponentScanAnnotationParser:
  2.8 ContextAnnotationAutowireCandidateResolver:
  2.9 ImportSelector: 注入的是可以配置为 Configuration 类型的类


参考资料:
  https://blog.csdn.net/qq_26000415/article/details/78947283

设计总结
  1. 架构体系建立
    1.1 需要有元数据类型 Meta
    1.2 建立匹配体系,