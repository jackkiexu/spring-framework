/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.aop.aspectj.autoproxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aspectj.util.PartialOrder;
import org.aspectj.util.PartialOrder.PartialComparable;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJPointcutAdvisor;
import org.springframework.aop.aspectj.AspectJProxyUtils;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * {@link org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator}
 * subclass that exposes AspectJ's invocation context and understands AspectJ's rules
 * for advice precedence when multiple pieces of advice come from the same aspect.
 *
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 *
 * 参考资料 http://www.zzcode.cn/springaop/thread-113.html
 * BeanPostProcessor 接口的作用是: 如果我们需要在 Spring 容器完成 bean 的实例化, 配置和其他的初始化前后添加一些自己的逻辑处理
 * 我们就可以定义一个或者多个 BeanPostProcessor 接口实现, 然后注册到容器中, 所以在创建类之前会调用 BeanPostProcessor 的 postProcessBeforeInstantiation 方法
 *
 * 当调用者通过 getBean(name) 向容器寻找 bean 时, 如果容器注册了
 * org.springframework.beans.factory.config.instantiationAwareBeanPostProcessor 接口, 在实例 Bean 之前
 * 将调用该接口的 postProcessBeforeInstantiation() 方法, AspectJAwareAdvisorAutoProxyCreator 实现了
 * InstantiationAwareBeanPostProcessor. 所以当 getbean 时就会执行 postProcessBeforeInstantiation 方法
 *
 * AspectJAwareAdvisorAutoProxyCreator 提供对 <aop:config></> 声明式 AOP 的支持
 */
@SuppressWarnings("serial")
public class AspectJAwareAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator {

	private static final Comparator<Advisor> DEFAULT_PRECEDENCE_COMPARATOR = new AspectJPrecedenceComparator();


	/**
	 * Sort the rest by AspectJ precedence(优先权). If two pieces of advice have
	 * come from the same aspect they will have the same order.
	 * Advice from the same aspect is then further ordered according to the
	 * following rules:
	 * <ul>
	 * <li>if either of the pair is after advice, then the advice declared
	 * last gets highest precedence (runs last)</li>
	 * <li>otherwise the advice declared first gets highest precedence (runs first)</li>
	 * </ul>
	 * <p><b>Important:</b> Advisors are sorted in precedence order, from highest
	 * precedence to lowest. "On the way in" to a join point, the highest precedence
	 * advisor should run first. "On the way out" of a join point, the highest precedence
	 * advisor should run last.
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		List<PartiallyComparableAdvisorHolder> partiallyComparableAdvisors =
				new ArrayList<PartiallyComparableAdvisorHolder>(advisors.size());
		for (Advisor element : advisors) {
			partiallyComparableAdvisors.add(
					new PartiallyComparableAdvisorHolder(element, DEFAULT_PRECEDENCE_COMPARATOR));
		}
		List<PartiallyComparableAdvisorHolder> sorted =
				PartialOrder.sort(partiallyComparableAdvisors);
		if (sorted != null) {
			List<Advisor> result = new ArrayList<Advisor>(advisors.size());
			for (PartiallyComparableAdvisorHolder pcAdvisor : sorted) {
				result.add(pcAdvisor.getAdvisor());
			}
			return result;
		}
		else {
			return super.sortAdvisors(advisors);
		}
	}

	/**
	 * Adds an {@link ExposeInvocationInterceptor} to the beginning of the advice chain.
	 * These additional advices are needed when using AspectJ expression pointcuts
	 * and when using AspectJ-style advice.
	 */
	// 在 Advisor 链中增加 ExposeInvocationInterceptor.ADVISOR, 主要是为了后续得 Advice 中能获取到当前的 JointPoint, 也就是 MethodInvocation
	@Override
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
		AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary(candidateAdvisors);
	}

	@Override
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		// TODO: Consider optimization by caching the list of the aspect names
		List<Advisor> candidateAdvisors = findCandidateAdvisors();					// 这里的 Advisor 分为两类, 下面用到的是 基于 被Aspect注解修饰的类而生成的 Advisor
		for (Advisor advisor : candidateAdvisors) {
			if (advisor instanceof AspectJPointcutAdvisor) {
				if (((AbstractAspectJAdvice) advisor.getAdvice()).getAspectName().equals(beanName)) {	 // 判断类 是否 应该跳过 Spring AOP 修饰
					return true;
				}
			}
		}
		return super.shouldSkip(beanClass, beanName);
	}


	/**
	 * Implements AspectJ PartialComparable interface for defining partial orderings.
	 */
	private static class PartiallyComparableAdvisorHolder implements PartialComparable {

		private final Advisor advisor;

		private final Comparator<Advisor> comparator;

		public PartiallyComparableAdvisorHolder(Advisor advisor, Comparator<Advisor> comparator) {
			this.advisor = advisor;
			this.comparator = comparator;
		}

		@Override
		public int compareTo(Object obj) {
			Advisor otherAdvisor = ((PartiallyComparableAdvisorHolder) obj).advisor;
			return this.comparator.compare(this.advisor, otherAdvisor);
		}

		@Override
		public int fallbackCompareTo(Object obj) {
			return 0;
		}

		public Advisor getAdvisor() {
			return this.advisor;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			Advice advice = this.advisor.getAdvice();
			sb.append(ClassUtils.getShortName(advice.getClass()));
			sb.append(": ");
			if (this.advisor instanceof Ordered) {
				sb.append("order ").append(((Ordered) this.advisor).getOrder()).append(", ");
			}
			if (advice instanceof AbstractAspectJAdvice) {
				AbstractAspectJAdvice ajAdvice = (AbstractAspectJAdvice) advice;
				sb.append(ajAdvice.getAspectName());
				sb.append(", declaration order ");
				sb.append(ajAdvice.getDeclarationOrder());
			}
			return sb.toString();
		}
	}

}
