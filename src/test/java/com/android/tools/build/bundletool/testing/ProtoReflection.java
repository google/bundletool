/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tools.build.bundletool.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

/** Utility class for Java reflection on Protocol buffer messages. */
public final class ProtoReflection {

  /** Invokes the {@code .getDefaultInstance()} of the proto message class. */
  @SuppressWarnings("unchecked") // Safe by contract of the `getDefaultInstance` method.
  public static <T extends Message> T getDefaultInstance(Class<T> clazz) {
    try {
      Method method = clazz.getMethod("getDefaultInstance");
      return (T) method.invoke(clazz);
    } catch (Exception e) {
      throw new RuntimeException("Failed to get default instance for proto: " + clazz.getName(), e);
    }
  }

  /**
   * Gets the {@link Class} object corresponding to the given message field.
   *
   * <p>If the field is repeated, then returns the class of the single item, rather than the
   * collection class.
   */
  @SuppressWarnings("unchecked") // The unchecked cast is executed for proto message field only.
  public static <T extends Message> Class<? extends Message> getJavaClassOfMessageField(
      T message, FieldDescriptor field) {
    checkArgument(field.getType().getJavaType().equals(JavaType.MESSAGE));

    if (field.isRepeated()) {
      String fieldGetterName = getterNameForProtoField(field);
      try {
        Method fieldGetter = message.getClass().getMethod(fieldGetterName);
        ParameterizedType fieldTypeArg = (ParameterizedType) fieldGetter.getGenericReturnType();
        checkState(
            fieldTypeArg.getActualTypeArguments().length == 1,
            "Collection representing a repeated field should have exactly one type argument.");
        return (Class<? extends Message>) fieldTypeArg.getActualTypeArguments()[0];
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Failed to resolve getter of repeated field "
                + field.getName()
                + " in proto "
                + message.getClass().getName(),
            e);
      }
    } else {
      return (Class<? extends Message>) message.getField(field).getClass();
    }
  }

  private static String getterNameForProtoField(FieldDescriptor field) {
    String capitalizedFieldName =
        field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
    return "get" + capitalizedFieldName + (field.isRepeated() ? "List" : "");
  }

  private ProtoReflection() {}
}
