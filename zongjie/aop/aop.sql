1. MethodInterceptor 策略模式
    AbstractTraceInterceptor 实现策略
      AbstractMonitorInterceptor
        JamonPerformanceMonitorInterceptor
        PerformanceMonitorInterceptor
      CustomizableTraceInterceptor
        StubCustomizableTraceInterceptor
      SimpleTraceInterceptor
        DebugInterceptor