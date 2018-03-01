SpringMVC 成员组件

1. ParameterNameDiscoverer 接口

1. HttpMessageConverter 接口
  主要是2个方法, 将 Http 请求来的数据 进行读取, 转换, 最后将处理的数据进行转换

  1.1 HttpInputMessage
    封装请求消息
  2.2 HttpOutputMessage
    封装处理后的消息

1. GenericConverter 与 Converter<S, T>, ConversionService, PropertyEditorRegistry, PropertyEditor, WebDataBinder, RequestResponseBodyAdviceChain 接口
  参考:
    http://www.logicbig.com/tutorials/spring-framework/spring-core/conversion-service/
    java在运行时获取泛型类型 ?????

2. HandlerMethodArgumentResolver 接口
  @PathVariable   -->  PathVariableMethodArgumentResolver
  @RequestParam   -->  RequestParamMethodArgumentResolver
  @RequestBody    -->  RequestResponseBodyMethodProcessor

  HandlerMethodArgumentResolverComposite

3. HandlerMethodReturnValueHandler 接口
  @@ControllerAdvice
  @ResponseBody   --> RequestResponseBodyMethodProcessor

2. HandlerMapping 接口
  2.1 SimpleUrlHandlerMapping 在xml中配置好映射关系的 HandlerMapping
  2.2 BeanNameUrlHandlerMapping 以 beanName 为映射 key 的 HandlerMapping <-- 继承 AbstractDetectingUrlHandlerMapping, 自动获取ApplicationContext中的所有类
  2.3 RequestMappingHandlerMapping

3. HandlerAdapter 接口

4. ContentNegotiationStrategy 接口

5. 经典的 策略 + 模版 + 组合 这种模式设计
  HandlerExceptionResolver 接口

1. SpringMVC  组件概述
   1.0 Converter
   1.0 PropertyEditor
   1.0 DataBinder
   1.0 ParameterNameDiscoverer
   1.0 ContentNegotiationStrategy
   1.1 DispatcherServlet
   1.2 Controller
   1.0 @ControllerAdvice
   1.3 HandlerMapping
   1.4 HandlerAdapter
   1.5 HttpMessageConverter
   1.6 HandlerMethodArgumentResolver
   1.7 HandlerMethodReturnValueHandler
   1.8 HandlerExceptionResolver
   1.9 ViewResolver
   1.10 常用 NamespaceHandler
2. SpringMVC  启动过程
3. SpringMVC  一次请求过程

参考资料:
  http://www.cnblogs.com/fangjian0423/tag/springmvc/
  http://www.cnblogs.com/question-sky/tag/SpringMVC/