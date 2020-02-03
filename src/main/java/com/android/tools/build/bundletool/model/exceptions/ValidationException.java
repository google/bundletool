/*
 * Copyright (C) 2017 The Android Open Source Project
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

import javax.annotation.CheckReturnValue;

/** Indicates invalid input (eg. bundle, bundle modules or other files). */
public class ValidationException extends CommandExecutionException {

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ValidationException(Throwable cause) {
    super(cause);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for the {@link ValidationException}. */
  public static class Builder extends CommandExecutionException.Builder {
    @Override
    @CheckReturnValue
    public ValidationException build() {
      if (message != null) {
        if (cause != null) {
          return new ValidationException(message, cause);
        } else {
          return new ValidationException(message);
        }
      } else {
        if (cause != null) {
          return new ValidationException(cause);
        } else {
          return new ValidationException("");
        }
      }
    }
  }
}
