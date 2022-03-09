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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.Nullable;

public class InternalExceptionBuilder<T extends BundleToolException> {
  private final ExceptionCreator<T> creator;

  @Nullable protected Throwable cause;
  @Nullable protected String internalMessage;

  InternalExceptionBuilder(ExceptionCreator<T> creator) {
    this.creator = creator;
  }

  public InternalExceptionBuilder<T> withInternalMessage(String internalMessage) {
    this.internalMessage = internalMessage;
    return this;
  }

  @FormatMethod
  public InternalExceptionBuilder<T> withInternalMessage(
      @FormatString String message, Object... args) {
    this.internalMessage = String.format(checkNotNull(message), args);
    return this;
  }

  public InternalExceptionBuilder<T> withCause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  @CheckReturnValue
  public T build() {
    String effectiveInternalMessage =
        internalMessage == null ? (cause == null ? "" : cause.toString()) : internalMessage;
    return this.creator.create(/* userMessage= */ "", effectiveInternalMessage, cause);
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
