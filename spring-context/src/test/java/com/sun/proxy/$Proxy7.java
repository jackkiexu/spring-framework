package com.sun.proxy;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.core.DecoratingProxy;
import org.springframework.tests.sample.beans.INestedTestBean;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.IndexedTestBean;

public final class $Proxy7 extends Proxy
  implements ITestBean, SpringProxy, Advised, DecoratingProxy
{
  private static Method m1;
  private static Method m40;
  private static Method m18;
  private static Method m22;
  private static Method m36;
  private static Method m47;
  private static Method m7;
  private static Method m15;
  private static Method m34;
  private static Method m46;
  private static Method m9;
  private static Method m3;
  private static Method m14;
  private static Method m39;
  private static Method m2;
  private static Method m12;
  private static Method m28;
  private static Method m29;
  private static Method m6;
  private static Method m17;
  private static Method m37;
  private static Method m32;
  private static Method m26;
  private static Method m25;
  private static Method m44;
  private static Method m48;
  private static Method m10;
  private static Method m20;
  private static Method m27;
  private static Method m31;
  private static Method m42;
  private static Method m43;
  private static Method m0;
  private static Method m23;
  private static Method m19;
  private static Method m24;
  private static Method m30;
  private static Method m4;
  private static Method m11;
  private static Method m13;
  private static Method m49;
  private static Method m5;
  private static Method m45;
  private static Method m50;
  private static Method m35;
  private static Method m16;
  private static Method m8;
  private static Method m38;
  private static Method m21;
  private static Method m41;
  private static Method m33;

  public $Proxy7(InvocationHandler paramInvocationHandler)
  {
    super(paramInvocationHandler);
  }

  static
  {
    try
    {
      m1 = Class.forName("java.lang.Object").getMethod("equals", new Class[] { Class.forName("java.lang.Object") });
      m40 = Class.forName("org.springframework.aop.framework.Advised").getMethod("isExposeProxy", new Class[0]);
      m18 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setNestedIntArray", new Class[] { Class.forName("[[I") });
      m22 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getLawyer", new Class[0]);
      m36 = Class.forName("org.springframework.aop.framework.Advised").getMethod("removeAdvisor", new Class[] { Integer.TYPE });
      m47 = Class.forName("org.springframework.aop.framework.Advised").getMethod("getProxiedInterfaces", new Class[0]);
      m7 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getSpouse", new Class[0]);
      m15 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getSomeIntArray", new Class[0]);
      m34 = Class.forName("org.springframework.aop.framework.Advised").getMethod("isInterfaceProxied", new Class[] { Class.forName("java.lang.Class") });
      m46 = Class.forName("org.springframework.aop.framework.Advised").getMethod("removeAdvice", new Class[] { Class.forName("org.aopalliance.aop.Advice") });
      m9 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getNestedIntegerArray", new Class[0]);
      m3 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getName", new Class[0]);
      m14 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setNestedIntegerArray", new Class[] { Class.forName("[[Ljava.lang.Integer;") });
      m39 = Class.forName("org.springframework.aop.framework.Advised").getMethod("setExposeProxy", new Class[] { Boolean.TYPE });
      m2 = Class.forName("java.lang.Object").getMethod("toString", new Class[0]);
      m12 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setSpouse", new Class[] { Class.forName("org.springframework.tests.sample.beans.ITestBean") });
      m28 = Class.forName("org.springframework.aop.framework.Advised").getMethod("indexOf", new Class[] { Class.forName("org.aopalliance.aop.Advice") });
      m29 = Class.forName("org.springframework.aop.framework.Advised").getMethod("isFrozen", new Class[0]);
      m6 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getAge", new Class[0]);
      m17 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getNestedIntArray", new Class[0]);
      m37 = Class.forName("org.springframework.aop.framework.Advised").getMethod("replaceAdvisor", new Class[] { Class.forName("org.springframework.aop.Advisor"), Class.forName("org.springframework.aop.Advisor") });
      m32 = Class.forName("org.springframework.aop.framework.Advised").getMethod("setPreFiltered", new Class[] { Boolean.TYPE });
      m26 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getStringArray", new Class[0]);
      m25 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("unreliableFileOperation", new Class[0]);
      m44 = Class.forName("org.springframework.aop.framework.Advised").getMethod("addAdvisor", new Class[] { Class.forName("org.springframework.aop.Advisor") });
      m48 = Class.forName("org.springframework.aop.framework.Advised").getMethod("isProxyTargetClass", new Class[0]);
      m10 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getSomeIntegerArray", new Class[0]);
      m20 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("returnsThis", new Class[0]);
      m27 = Class.forName("org.springframework.aop.framework.Advised").getMethod("indexOf", new Class[] { Class.forName("org.springframework.aop.Advisor") });
      m31 = Class.forName("org.springframework.aop.framework.Advised").getMethod("getTargetSource", new Class[0]);
      m42 = Class.forName("org.springframework.aop.framework.Advised").getMethod("addAdvice", new Class[] { Integer.TYPE, Class.forName("org.aopalliance.aop.Advice") });
      m43 = Class.forName("org.springframework.aop.framework.Advised").getMethod("addAdvice", new Class[] { Class.forName("org.aopalliance.aop.Advice") });
      m0 = Class.forName("java.lang.Object").getMethod("hashCode", new Class[0]);
      m23 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getNestedIndexedBean", new Class[0]);
      m19 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("exceptional", new Class[] { Class.forName("java.lang.Throwable") });
      m24 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("haveBirthday", new Class[0]);
      m30 = Class.forName("org.springframework.aop.framework.Advised").getMethod("setTargetSource", new Class[] { Class.forName("org.springframework.aop.TargetSource") });
      m4 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setName", new Class[] { Class.forName("java.lang.String") });
      m11 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setSomeIntegerArray", new Class[] { Class.forName("[Ljava.lang.Integer;") });
      m13 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getSpouses", new Class[0]);
      m49 = Class.forName("org.springframework.aop.framework.Advised").getMethod("getTargetClass", new Class[0]);
      m5 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setAge", new Class[] { Integer.TYPE });
      m45 = Class.forName("org.springframework.aop.framework.Advised").getMethod("addAdvisor", new Class[] { Integer.TYPE, Class.forName("org.springframework.aop.Advisor") });
      m50 = Class.forName("org.springframework.core.DecoratingProxy").getMethod("getDecoratedClass", new Class[0]);
      m35 = Class.forName("org.springframework.aop.framework.Advised").getMethod("removeAdvisor", new Class[] { Class.forName("org.springframework.aop.Advisor") });
      m16 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setSomeIntArray", new Class[] { Class.forName("[I") });
      m8 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("setStringArray", new Class[] { Class.forName("[Ljava.lang.String;") });
      m38 = Class.forName("org.springframework.aop.framework.Advised").getMethod("toProxyConfigString", new Class[0]);
      m21 = Class.forName("org.springframework.tests.sample.beans.ITestBean").getMethod("getDoctor", new Class[0]);
      m41 = Class.forName("org.springframework.aop.framework.Advised").getMethod("getAdvisors", new Class[0]);
      m33 = Class.forName("org.springframework.aop.framework.Advised").getMethod("isPreFiltered", new Class[0]);
    }
    catch (NoSuchMethodException localNoSuchMethodException)
    {
      throw new NoSuchMethodError(localNoSuchMethodException.getMessage());
    }
    catch (ClassNotFoundException localClassNotFoundException)
    {
      throw new NoClassDefFoundError(localClassNotFoundException.getMessage());
    }
  }

  public final boolean equals(Object paramObject)
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m1, new Object[] { paramObject })).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final String toString()
  {
    try
    {
      return (String)this.h.invoke(this, m2, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int hashCode()
  {
    try
    {
      return ((Integer)this.h.invoke(this, m0, null)).intValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int indexOf(Advisor paramAdvisor)
  {
    try
    {
      return ((Integer)this.h.invoke(this, m27, new Object[] { paramAdvisor })).intValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int indexOf(Advice paramAdvice)
  {
    try
    {
      return ((Integer)this.h.invoke(this, m28, new Object[] { paramAdvice })).intValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final String getName()
  {
    try
    {
      return (String)this.h.invoke(this, m3, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setName(String paramString)
  {
    try
    {
      this.h.invoke(this, m4, new Object[] { paramString });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean isFrozen()
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m29, null)).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setTargetSource(TargetSource paramTargetSource)
  {
    try
    {
      this.h.invoke(this, m30, new Object[] { paramTargetSource });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final TargetSource getTargetSource()
  {
    try
    {
      return (TargetSource)this.h.invoke(this, m31, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setPreFiltered(boolean paramBoolean)
  {
    try
    {
      this.h.invoke(this, m32, new Object[] { Boolean.valueOf(paramBoolean) });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean isPreFiltered()
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m33, null)).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean isInterfaceProxied(Class paramClass)
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m34, new Object[] { paramClass })).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean removeAdvisor(Advisor paramAdvisor)
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m35, new Object[] { paramAdvisor })).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void removeAdvisor(int paramInt)
    throws AopConfigException
  {
    try
    {
      this.h.invoke(this, m36, new Object[] { Integer.valueOf(paramInt) });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean replaceAdvisor(Advisor paramAdvisor1, Advisor paramAdvisor2)
    throws AopConfigException
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m37, new Object[] { paramAdvisor1, paramAdvisor2 })).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final String toProxyConfigString()
  {
    try
    {
      return (String)this.h.invoke(this, m38, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setExposeProxy(boolean paramBoolean)
  {
    try
    {
      this.h.invoke(this, m39, new Object[] { Boolean.valueOf(paramBoolean) });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean isExposeProxy()
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m40, null)).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Class getDecoratedClass()
  {
    try
    {
      return (Class)this.h.invoke(this, m50, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setAge(int paramInt)
  {
    try
    {
      this.h.invoke(this, m5, new Object[] { Integer.valueOf(paramInt) });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int getAge()
  {
    try
    {
      return ((Integer)this.h.invoke(this, m6, null)).intValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Advisor[] getAdvisors()
  {
    try
    {
      return (Advisor[])this.h.invoke(this, m41, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void addAdvice(Advice paramAdvice)
    throws AopConfigException
  {
    try
    {
      this.h.invoke(this, m43, new Object[] { paramAdvice });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void addAdvice(int paramInt, Advice paramAdvice)
    throws AopConfigException
  {
    try
    {
      this.h.invoke(this, m42, new Object[] { Integer.valueOf(paramInt), paramAdvice });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void addAdvisor(Advisor paramAdvisor)
    throws AopConfigException
  {
    try
    {
      this.h.invoke(this, m44, new Object[] { paramAdvisor });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void addAdvisor(int paramInt, Advisor paramAdvisor)
    throws AopConfigException
  {
    try
    {
      this.h.invoke(this, m45, new Object[] { Integer.valueOf(paramInt), paramAdvisor });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean removeAdvice(Advice paramAdvice)
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m46, new Object[] { paramAdvice })).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final ITestBean getSpouse()
  {
    try
    {
      return (ITestBean)this.h.invoke(this, m7, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Class[] getProxiedInterfaces()
  {
    try
    {
      return (Class[])this.h.invoke(this, m47, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Class getTargetClass()
  {
    try
    {
      return (Class)this.h.invoke(this, m49, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final boolean isProxyTargetClass()
  {
    try
    {
      return ((Boolean)this.h.invoke(this, m48, null)).booleanValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setStringArray(String[] paramArrayOfString)
  {
    try
    {
      this.h.invoke(this, m8, new Object[] { paramArrayOfString });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Integer[][] getNestedIntegerArray()
  {
    try
    {
      return (Integer[][])this.h.invoke(this, m9, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final Integer[] getSomeIntegerArray()
  {
    try
    {
      return (Integer[])this.h.invoke(this, m10, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setSomeIntegerArray(Integer[] paramArrayOfInteger)
  {
    try
    {
      this.h.invoke(this, m11, new Object[] { paramArrayOfInteger });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setSpouse(ITestBean paramITestBean)
  {
    try
    {
      this.h.invoke(this, m12, new Object[] { paramITestBean });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final ITestBean[] getSpouses()
  {
    try
    {
      return (ITestBean[])this.h.invoke(this, m13, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setNestedIntegerArray(Integer[][] paramArrayOfInteger)
  {
    try
    {
      this.h.invoke(this, m14, new Object[] { paramArrayOfInteger });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int[] getSomeIntArray()
  {
    try
    {
      return (int[])this.h.invoke(this, m15, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setSomeIntArray(int[] paramArrayOfInt)
  {
    try
    {
      this.h.invoke(this, m16, new Object[] { paramArrayOfInt });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int[][] getNestedIntArray()
  {
    try
    {
      return (int[][])this.h.invoke(this, m17, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void setNestedIntArray(int[][] paramArrayOfInt)
  {
    try
    {
      this.h.invoke(this, m18, new Object[] { paramArrayOfInt });
      return;
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void exceptional(Throwable paramThrowable)
    throws Throwable
  {
    this.h.invoke(this, m19, new Object[] { paramThrowable });
  }

  public final Object returnsThis()
  {
    try
    {
      return (Object)this.h.invoke(this, m20, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final INestedTestBean getDoctor()
  {
    try
    {
      return (INestedTestBean)this.h.invoke(this, m21, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final INestedTestBean getLawyer()
  {
    try
    {
      return (INestedTestBean)this.h.invoke(this, m22, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final IndexedTestBean getNestedIndexedBean()
  {
    try
    {
      return (IndexedTestBean)this.h.invoke(this, m23, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final int haveBirthday()
  {
    try
    {
      return ((Integer)this.h.invoke(this, m24, null)).intValue();
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final void unreliableFileOperation()
    throws IOException
  {
    try
    {
      this.h.invoke(this, m25, null);
      return;
    }
    catch (Error|RuntimeException|IOException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }

  public final String[] getStringArray()
  {
    try
    {
      return (String[])this.h.invoke(this, m26, null);
    }
    catch (Error|RuntimeException localError)
    {
      throw localError;
    }
    catch (Throwable localThrowable)
    {
      throw new UndeclaredThrowableException(localThrowable);
    }
  }
}

/* Location:           /Users/xjk/Desktop/tmp/2017092301/
 * Qualified Name:     com.sun.proxy..Proxy7
 * JD-Core Version:    0.6.2
 */