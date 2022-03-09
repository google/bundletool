/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nullable;

public class UserExceptionBuilder<T extends BundleToolException> {
  private final ExceptionCreator<T> creator;

  @Nullable private Throwable cause;
  @Nullable private String userMessage;

  UserExceptionBuilder(ExceptionCreator<T> creator) {
    this.creator = creator;
  }

  public UserExceptionBuilder<T> withUserMessage(String userMessage) {
    this.userMessage = userMessage;
    return this;
  }

  @FormatMethod
  public UserExceptionBuilder<T> withUserMessage(@FormatString String message, Object... args) {
    this.userMessage = String.format(checkNotNull(message), args);
    return this;
  }

  public UserExceptionBuilder<T> withCause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  @CheckReturnValue
  public T build() {
    String effectiveUserMessage = nullToEmpty(userMessage);
    String effectiveInternalMessage =
        userMessage == null ? (cause == null ? "" : cause.toString()) : userMessage;
    return this.creator.create(effectiveUserMessage, effectiveInternalMessage, cause);
  }

  /**
   * TriFunction to reference exception constructor and specify which exception type should be
   * built.
   */
  @FunctionalInterface
  interface ExceptionCreator<T extends BundleToolException> {
    T create(String userMessage, String internalMessage, Throwable cause);
  }
}
