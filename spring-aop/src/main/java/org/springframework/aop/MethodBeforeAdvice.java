/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.aop;

import java.lang.reflect.Method;

/**
 * Advice invoked before a method is invoked. Such advices cannot
 * prevent the method call proceeding, unless they throw a Throwable.
 *
 * @see AfterReturningAdvice
 * @see ThrowsAdvice
 *
 * @author Rod Johnson
 * 参考资料 http://cxis.me/2017/04/12/Spring%E4%B8%ADAOP%E6%BA%90%E7%A0%81%E6%B7%B1%E5%85%A5%E8%A7%A3%E6%9E%90/
 * 表示在方法前调用的增强器, 方法前置增强器 不能阻止方法的调用, 但是能抛异常来使目标方法不继续执行
 */
public interface MethodBeforeAdvice extends BeforeAdvice {

	/**
	 * Callback before a given method is invoked.
	 * @param method method being invoked
	 * @param args arguments to the method
	 * @param target target of the method invocation. May be {@code null}.
	 * @throws Throwable if this object wishes to abort the call.
	 * Any exception thrown will be returned to the caller if it's
	 * allowed by the method signature. Otherwise the exception
	 * will be wrapped as a runtime exception.
	 */
	// 在给定方法调用之前调用该方法
	void before(Method method, Object[] args, Object target) throws Throwable;

}
