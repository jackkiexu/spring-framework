/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop.aspectj;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.reflect.Factory;
import org.junit.Test;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.TestBean;

import static org.junit.Assert.*;

/**
 * @author Rod Johnson
 * @author Chris Beams
 * @author Ramnivas Laddad
 * @since 2.0
 */
public final class MethodInvocationProceedingJoinPointTests {

	@Test
	public void testingBindingWithJoinPoint() {
		try {
			AbstractAspectJAdvice.currentJoinPoint();
			fail("Needs to be bound by interceptor action");
		}
		catch (IllegalStateException ex) {
			// expected
			System.out.println(ex);
		}
	}

	@Test
	public void testingBindingWithProceedingJoinPoint() {
		try {
			AbstractAspectJAdvice.currentJoinPoint();
			fail("Needs to be bound by interceptor action");
		}
		catch (IllegalStateException ex) {
			// expected
			System.out.println(ex);
		}
	}

	@Test
	public void testCanGetMethodSignatureFromJoinPoint() {
		final Object raw = new TestBean();
		// Will be set by advice during a method call
		final int newAge = 23;

		ProxyFactory pf = new ProxyFactory(raw);
		pf.setExposeProxy(true);								// 将代理类暴露给 ThreadLocal
		pf.addAdvisor(ExposeInvocationInterceptor.ADVISOR);		// 在 AdvisedSupport 中增加 ExposeInvocationInterceptor
		pf.addAdvice(new MethodBeforeAdvice() {					// 这里表面上增加 BeforeAdvice, 其实或包裹成 DefaultPointcutAdvisor 放入 Advisor里面
			private int depth;

			@Override
			public void before(Method method, Object[] args, Object target) throws Throwable {
				JoinPoint jp = AbstractAspectJAdvice.currentJoinPoint();
				assertTrue("Method named in toString", jp.toString().contains(method.getName()));
				// Ensure that these don't cause problems
				String jpShortStr = jp.toShortString();
				String jpLongStr = jp.toLongString();
				System.out.println("jpShortStr:" + jpShortStr);
				System.out.println("jpLongStr:" + jpLongStr);

				assertSame(target, AbstractAspectJAdvice.currentJoinPoint().getTarget());					// 这里就是获取 MethodInvocationProceedingJoinPoint -> ReflectiveMethodInvocation -> 里面的 target
				assertFalse(AopUtils.isAopProxy(AbstractAspectJAdvice.currentJoinPoint().getTarget()));

				ITestBean thisProxy = (ITestBean) AbstractAspectJAdvice.currentJoinPoint().getThis();		// 这里的 This 代表的是执行 AOP 里面 Advice 的对象, 也就是 生成的 Proxy
				assertTrue(AopUtils.isAopProxy(AbstractAspectJAdvice.currentJoinPoint().getThis()));

				assertNotSame(target, thisProxy);								// 用 Target 与 Proxy 对比

				// Check getting again doesn't cause a problem
				assertSame(thisProxy, AbstractAspectJAdvice.currentJoinPoint().getThis());

				// Try reentrant call--will go through this advice.
				// Be sure to increment depth to avoid infinite recursion
				if (depth++ == 0) {
					// Check that toString doesn't cause a problem
					thisProxy.toString();
					// Change age, so this will be returned by invocation
					thisProxy.setAge(newAge);
					assertEquals(newAge, thisProxy.getAge());
				}

				assertSame(AopContext.currentProxy(), thisProxy);					// 比价 Proxy
				assertSame(target, raw);

				Signature signature = AbstractAspectJAdvice.currentJoinPoint().getSignature();

				assertSame(method.getName(), AbstractAspectJAdvice.currentJoinPoint().getSignature().getName());
				assertEquals(method.getModifiers(), AbstractAspectJAdvice.currentJoinPoint().getSignature().getModifiers());

				MethodSignature msig = (MethodSignature) AbstractAspectJAdvice.currentJoinPoint().getSignature();
				assertSame("Return same MethodSignature repeatedly", msig, AbstractAspectJAdvice.currentJoinPoint().getSignature());
				assertSame("Return same JoinPoint repeatedly", AbstractAspectJAdvice.currentJoinPoint(), AbstractAspectJAdvice.currentJoinPoint());

				assertEquals(method.getDeclaringClass(), msig.getDeclaringType());
				assertTrue(Arrays.equals(method.getParameterTypes(), msig.getParameterTypes()));
				assertEquals(method.getReturnType(), msig.getReturnType());
				assertTrue(Arrays.equals(method.getExceptionTypes(), msig.getExceptionTypes()));
				msig.toLongString();
				msig.toShortString();
			}
		});
		ITestBean itb = (ITestBean) pf.getProxy();
		// Any call will do
		assertEquals("Advice reentrantly set age", newAge, itb.getAge());
	}

	@Test
	public void testCanGetSourceLocationFromJoinPoint() {
		final Object raw = new TestBean();
		ProxyFactory pf = new ProxyFactory(raw);
		pf.addAdvisor(ExposeInvocationInterceptor.ADVISOR);
		pf.addAdvice(new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) throws Throwable {
				SourceLocation sloc = AbstractAspectJAdvice.currentJoinPoint().getSourceLocation();
				assertEquals("Same source location must be returned on subsequent requests", sloc, AbstractAspectJAdvice.currentJoinPoint().getSourceLocation());
				assertEquals(TestBean.class, sloc.getWithinType());
				try {
					sloc.getLine();
					fail("Can't get line number");
				}
				catch (UnsupportedOperationException ex) {
					// Expected
				}

				try {
					sloc.getFileName();
					fail("Can't get file name");
				}
				catch (UnsupportedOperationException ex) {
					// Expected
				}
			}
		});
		ITestBean itb = (ITestBean) pf.getProxy();
		// Any call will do
		itb.getAge();
	}

	@Test
	public void testCanGetStaticPartFromJoinPoint() {
		final Object raw = new TestBean();
		ProxyFactory pf = new ProxyFactory(raw);
		pf.addAdvisor(ExposeInvocationInterceptor.ADVISOR);
		pf.addAdvice(new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) throws Throwable {
				StaticPart staticPart = AbstractAspectJAdvice.currentJoinPoint().getStaticPart();
				assertEquals("Same static part must be returned on subsequent requests", staticPart, AbstractAspectJAdvice.currentJoinPoint().getStaticPart());
				assertEquals(ProceedingJoinPoint.METHOD_EXECUTION, staticPart.getKind());
				assertSame(AbstractAspectJAdvice.currentJoinPoint().getSignature(), staticPart.getSignature());
				assertEquals(AbstractAspectJAdvice.currentJoinPoint().getSourceLocation(), staticPart.getSourceLocation());
			}
		});
		ITestBean itb = (ITestBean) pf.getProxy();
		// Any call will do
		itb.getAge();
	}

	@Test
	public void toShortAndLongStringFormedCorrectly() throws Exception {
		final Object raw = new TestBean();
		ProxyFactory pf = new ProxyFactory(raw);
		pf.addAdvisor(ExposeInvocationInterceptor.ADVISOR);
		pf.addAdvice(new MethodBeforeAdvice() {
			@Override
			public void before(Method method, Object[] args, Object target) throws Throwable {
				// makeEncSJP, although meant for computing the enclosing join point,
				// it serves our purpose here
				JoinPoint.StaticPart aspectJVersionJp = Factory.makeEncSJP(method);
				JoinPoint jp = AbstractAspectJAdvice.currentJoinPoint();

				System.out.println("aspectJVersionJp.getSignature().toLongString():" + aspectJVersionJp.getSignature().toLongString());
				System.out.println("jp.getSignature().toLongString():" + jp.getSignature().toLongString());
				System.out.println("aspectJVersionJp.getSignature().toShortString():" + aspectJVersionJp.getSignature().toShortString());
				System.out.println("jp.getSignature().toShortString():" + jp.getSignature().toShortString());
				System.out.println("aspectJVersionJp.getSignature().toString():" + aspectJVersionJp.getSignature().toString());
				System.out.println("jp.getSignature().toString():" + jp.getSignature().toString());
				assertEquals(aspectJVersionJp.getSignature().toLongString(), jp.getSignature().toLongString());
				assertEquals(aspectJVersionJp.getSignature().toShortString(), jp.getSignature().toShortString());
				assertEquals(aspectJVersionJp.getSignature().toString(), jp.getSignature().toString());

				System.out.println("aspectJVersionJp.toLongString():" +aspectJVersionJp.toLongString());
				System.out.println("jp.toLongString():" + jp.toLongString());
				System.out.println("jp.toShortString():" + jp.toShortString());
				assertEquals(aspectJVersionJp.toLongString(), jp.toLongString());
				assertEquals(aspectJVersionJp.toShortString(), jp.toShortString());
				assertEquals(aspectJVersionJp.toString(), jp.toString());
			}
		});
		ITestBean itb = (ITestBean) pf.getProxy();
		itb.getAge();
		itb.setName("foo");
		itb.getDoctor();
		itb.getStringArray();
		itb.getSpouse();
		itb.setSpouse(new TestBean());
		try {
			itb.unreliableFileOperation();
		}
		catch (IOException ex) {
			// we don't realy care...
			System.out.println(ex);
		}
	}

}
