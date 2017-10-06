1. 事务的分类
  1). 编程式事务
    a) 使用 PlatformTransactionManager 来操作
    b) 基于 TransactionTemplate 的编程式事务管理

  2). 声明式事务
    a) 基于 TransactionInterceptor, ProxyFactoryBean
    b) 基于 TransactionProxyFactoryBean
    c) 基于 <tx>, <aop> 命名空间的声明式事务管理
    d) 基于 @Transactional 的声明式事务管理


参考资料:
1. Spring 管理事务的总类
  https://www.ibm.com/developerworks/cn/education/opensource/os-cn-spring-trans/index.html


DataSourceUtils

ConnectionHolder
TransactionSynchronizationManager