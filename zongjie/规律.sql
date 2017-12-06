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

8. 以 Adapter 为尾缀命名的类

