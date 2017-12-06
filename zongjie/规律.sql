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

3. 以 Support 为尾缀命名的类

4. 以 Registry 为尾缀命名的类

5. 以 Config 为尾缀命名的类

6. 以 Delegate 为尾缀命名的类

7. 以 Holder 为尾缀命名的类

8. 适配器模式, 含有 Adapter 关键字

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

19. 命令模式, 
