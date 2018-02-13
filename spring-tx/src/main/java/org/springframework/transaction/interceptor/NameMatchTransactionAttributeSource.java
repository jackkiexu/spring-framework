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

package org.springframework.transaction.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;

/**
 * Simple {@link TransactionAttributeSource} implementation that
 * allows attributes to be matched by registered name.
 *
 * @author Juergen Hoeller
 * @since 21.08.2003
 * @see #isMatch
 * @see MethodMapTransactionAttributeSource
 */
// 通过将 Properties 里面的属性转化成 methodName <--> TransactionAttribute 的TransactionAttributeSource
@SuppressWarnings("serial")
public class NameMatchTransactionAttributeSource implements TransactionAttributeSource, Serializable {

	/**
	 * Logger available to subclasses.
	 * <p>Static for optimal serialization.
	 */
	protected static final Log logger = LogFactory.getLog(NameMatchTransactionAttributeSource.class);

	/** Keys are method names; values are TransactionAttributes */
	private Map<String, TransactionAttribute> nameMap = new HashMap<String, TransactionAttribute>();


	/**
	 * Set a name/attribute map, consisting of method names
	 * (e.g. "myMethod") and TransactionAttribute instances
	 * (or Strings to be converted to TransactionAttribute instances).
	 * @see TransactionAttribute
	 * @see TransactionAttributeEditor
	 */
	public void setNameMap(Map<String, TransactionAttribute> nameMap) {
		for (Map.Entry<String, TransactionAttribute> entry : nameMap.entrySet()) {
			addTransactionalMethod(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Parses the given properties into a name/attribute map.
	 * Expects method names as keys and String attributes definitions as values,
	 * parsable into TransactionAttribute instances via TransactionAttributeEditor.
	 * @see #setNameMap
	 * @see TransactionAttributeEditor
	 */
	// 设置 配置的事务方法
	public void setProperties(Properties transactionAttributes) { // 通过将 Properties 里面的属性转化成 methodName <--> TransactionAttribute 的TransactionAttributeSource
		TransactionAttributeEditor tae = new TransactionAttributeEditor();
		Enumeration<?> propNames = transactionAttributes.propertyNames();
		while (propNames.hasMoreElements()) {
			String methodName = (String) propNames.nextElement();
			String value = transactionAttributes.getProperty(methodName);
			tae.setAsText(value);
			TransactionAttribute attr = (TransactionAttribute) tae.getValue();
			addTransactionalMethod(methodName, attr);
		}
	}

	/**
	 * Add an attribute for a transactional method.
	 * <p>Method names can be exact matches, or of the pattern "xxx*",
	 * "*xxx" or "*xxx*" for matching multiple methods.
	 * @param methodName the name of the method
	 * @param attr attribute associated with the method
	 */
	public void addTransactionalMethod(String methodName, TransactionAttribute attr) {
		if (logger.isDebugEnabled()) {
			logger.debug("Adding transactional method [" + methodName + "] with attribute [" + attr + "]");
		}
		this.nameMap.put(methodName, attr);
	}


	// 对调用的方法进行判断, 判断它是否是事务方法, 如果是事务方法, 那么取出相应的事务配置属性
	@Override
	public TransactionAttribute getTransactionAttribute(Method method, Class<?> targetClass) {
		// 判断当前目标调用的方法与配置的事务方法是否直接匹配
		if (!ClassUtils.isUserLevelMethod(method)) {
			return null;
		}

		// Look for direct name match.
		String methodName = method.getName();
		TransactionAttribute attr = this.nameMap.get(methodName);
		// 如果不能直接匹配, 就通过 PatternMatchUtils 的 simpleMatch 方法来进行匹配判断
		if (attr == null) {
			// Look for most specific name match.
			String bestNameMatch = null;
			for (String mappedName : this.nameMap.keySet()) {
				if (isMatch(methodName, mappedName) &&			// 方法名称匹配
						(bestNameMatch == null || bestNameMatch.length() <= mappedName.length())) {
					attr = this.nameMap.get(mappedName);
					bestNameMatch = mappedName;
				}
			}
		}

		return attr;
	}

	/**
	 * Return if the given method name matches the mapped name.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*" matches,
	 * as well as direct equality. Can be overridden in subclasses.
	 * @param methodName the method name of the class
	 * @param mappedName the name in the descriptor
	 * @return if the names match
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	// 事务方法的匹配判断, 详细的匹配过程在 PatternMatchUtils 中实现
	protected boolean isMatch(String methodName, String mappedName) {
		return PatternMatchUtils.simpleMatch(mappedName, methodName);
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NameMatchTransactionAttributeSource)) {
			return false;
		}
		NameMatchTransactionAttributeSource otherTas = (NameMatchTransactionAttributeSource) other;
		return ObjectUtils.nullSafeEquals(this.nameMap, otherTas.nameMap);
	}

	@Override
	public int hashCode() {
		return NameMatchTransactionAttributeSource.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + this.nameMap;
	}

}
