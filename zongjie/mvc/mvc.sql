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

2. HandlerMethodArgumentResolver 接口
  @PathVariable   -->  PathVariableMethodArgumentResolver
  @RequestParam   -->  RequestParamMethodArgumentResolver
  @RequestBody    -->  RequestResponseBodyMethodProcessor

  HandlerMethodArgumentResolverComposite

3. HandlerMethodReturnValueHandler 接口
  @@ControllerAdvice
  @ResponseBody   --> RequestResponseBodyMethodProcessor

2. HandlerMapping 接口

3. HandlerAdapter 接口

4. ContentNegotiationStrategy 接口