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