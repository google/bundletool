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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.bundle.Errors.BundleToolError;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Error indicating something went wrong during executing the command. */
public class CommandExecutionException extends RuntimeException {

  public CommandExecutionException(String message) {
    super(message);
  }

  public CommandExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  public CommandExecutionException(Throwable cause) {
    super(cause);
  }

  public BundleToolError toProto() {
    BundleToolError.Builder builder =
        BundleToolError.newBuilder().setExceptionMessage(getMessage());
    customizeProto(builder);
    return builder.build();
  }

  protected void customizeProto(BundleToolError.Builder builder) {}

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for the {@link CommandExecutionException} */
  public static class Builder {

    @Nullable protected Throwable cause;
    @Nullable protected String message;

    public Builder withCause(Throwable cause) {
      this.cause = cause;
      return this;
    }

    public Builder withMessage(String message) {
      this.message = message;
      return this;
    }

    @FormatMethod
    public Builder withMessage(@FormatString String message, Object... args) {
      this.message = String.format(checkNotNull(message), args);
      return this;
    }

    @CheckReturnValue
    public CommandExecutionException build() {
      if (message != null) {
        if (cause != null) {
          return new CommandExecutionException(message, cause);
        } else {
          return new CommandExecutionException(message);
        }
      } else {
        if (cause != null) {
          return new CommandExecutionException(cause);
        } else {
          return new CommandExecutionException("");
        }
      }
    }
  }
}
