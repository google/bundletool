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

import com.android.bundle.Errors.BundleToolError;
import com.android.bundle.Errors.BundleToolError.ErrorType;

/** Indicates meaningful exception thrown by bundletool. */
public abstract class BundleToolException extends RuntimeException {
  private final String userMessage;
  private final ErrorType errorType;

  BundleToolException(
      ErrorType errorType, String userMessage, String internalMessage, Throwable cause) {
    super(internalMessage, cause);
    this.errorType = errorType;
    this.userMessage = userMessage;
  }

  BundleToolException(ErrorType errorType, String userMessage, String internalMessage) {
    super(internalMessage);
    this.errorType = errorType;
    this.userMessage = userMessage;
  }

  public String getUserMessage() {
    return userMessage;
  }

  public final BundleToolError toProto() {
    return BundleToolError.newBuilder()
        .setExceptionMessage(getMessage())
        .setUserMessage(userMessage)
        .setErrorType(errorType)
        .build();
  }
}
