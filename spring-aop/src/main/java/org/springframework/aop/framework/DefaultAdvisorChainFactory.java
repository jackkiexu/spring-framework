/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.support.MethodMatchers;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 *
 * 参考资料 http://xsh5324.iteye.com/blog/1846862
 *
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	// 获取匹配 targetClass 与 method 的所有切面的通知
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		List<Object> interceptorList = new ArrayList<Object>(config.getAdvisors().length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		boolean hasIntroductions = hasMatchingIntroductions(config, actualClass);
		// 下面这个适配器将通知 [Advice] 包装成拦截器 [MethodInterceptor] [DefaultAdvisorAdapterRegistry] 适配器的默认实现
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();

		for (Advisor advisor : config.getAdvisors()) {
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// 判断此切面 [advisor] 是否匹配 targetClass
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					/** 通过对适配器将通知 [Advice] 包装成 MethodInterceptor, 这里为什么是个数组? 因为一个通知类
					 *  可能同时实现了前置通知[MethodBeforeAdvice], 后置通知[AfterReturingAdvice], 异常通知接口[ThrowsAdvice]
					 *   环绕通知 [MethodInterceptor], 这里会将每个通知统一包装成 MethodInterceptor
					 */
					MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					// 是否匹配 targetClass 类的 method 方法
					if (MethodMatchers.matches(mm, method, actualClass, hasIntroductions)) {
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							// 如果需要在运行时动态拦截方法的执行则创建一个简单的对象封装相关的数据, 它将延时
							// 到方法执行的时候验证要不要执行此通知
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				// 如果是引入切面的话则判断它是否适用于目标类, Spring 中默认的引入切面实现是 DefaultIntroductionAdvisor 类
				// 默认的引入通知是 DelegatingIntroductionInterceptor 它实现了 MethodInterceptor 接口s
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advised config, Class<?> actualClass) {
		for (int i = 0; i < config.getAdvisors().length; i++) {
			Advisor advisor = config.getAdvisors()[i];
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
