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

package org.springframework.core.convert.converter;

import org.springframework.core.convert.TypeDescriptor;

/**
 * A {@link GenericConverter} that may conditionally execute based on attributes
 * of the {@code source} and {@code target} {@link TypeDescriptor}.
 *
 * <p>See {@link ConditionalConverter} for details.
 *
 * @author Keith Donald
 * @author Phillip Webb
 * @since 3.0
 * @see GenericConverter
 * @see ConditionalConverter 将一个 source 转换成 一个或多个 对象, 运用一个/多个 converter
 *
 * 比如:
 * class Person {
 *     Integer age;
 *     String Name;
 * }
 * 这时可以新建一个 ConditionalGenericConverter
 *      PersonConverterToStringConditionGenericConverter
 *      其实就是将 Person 里面的各个属性变成 String, 然后再拼接起来
 */
public interface ConditionalGenericConverter extends GenericConverter, ConditionalConverter {

}
