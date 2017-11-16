package org.springframework.aop.framework.autoproxy;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.tests.aop.interceptor.NopInterceptor;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.TestBean;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by xujiankang on 2017/11/16.
 */
public class BeanNameAutoProxyCreatorTests2 {
    private static final Logger logger = LoggerFactory.getLogger(BeanNameAutoProxyCreatorTests.class);

    private BeanFactory beanFactory;

    @Before
    public void setUp() throws IOException {
        // Note that we need an ApplicationContext, not just a BeanFactory,
        // for post-processing and hence auto-proxying to work.
        beanFactory =
                new ClassPathXmlApplicationContext(getClass().getSimpleName() + "-context.xml", getClass());
    }

    @Test
    public void testJdkProxyWithDoubleProxying() {
        ITestBean tb = (ITestBean) beanFactory.getBean("doubleJdk");
        jdkAssertions(tb, 2);
        assertEquals("doubleJdk", tb.getName());
    }

    private void jdkAssertions(ITestBean tb, int nopInterceptorCount)  {
        NopInterceptor nop = (NopInterceptor) beanFactory.getBean("nopInterceptor");
        System.out.println("nop.getCount():" + nop.getCount());
        System.out.println("AopUtils.isJdkDynamicProxy(tb):" + AopUtils.isJdkDynamicProxy(tb));
        assertTrue(AopUtils.isJdkDynamicProxy(tb));
        int age = 5;
        tb.setAge(age);
        assertEquals(age, tb.getAge());
        System.out.println("nopInterceptorCount:" + nopInterceptorCount);
        System.out.println("nop.getCount():" + nop.getCount());
    }
}
