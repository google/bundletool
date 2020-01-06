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
package com.android.tools.build.bundletool.device;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import javax.annotation.CheckReturnValue;

/** Exception indicating that the device is incompatible with the App Bundle or APK Set. */
public final class IncompatibleDeviceException extends CommandExecutionException {

 public IncompatibleDeviceException(String message) {
    super(message);
  }

  public IncompatibleDeviceException(String message, Throwable cause) {
    super(message, cause);
  }

  public IncompatibleDeviceException(Throwable cause) {
    super(cause);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for the {@link IncompatibleDeviceException}. */
  public static class Builder extends CommandExecutionException.Builder {
    @Override
    @CheckReturnValue
    public IncompatibleDeviceException build() {
      if (message != null) {
        if (cause != null) {
          return new IncompatibleDeviceException(message, cause);
        } else {
          return new IncompatibleDeviceException(message);
        }
      } else {
        if (cause != null) {
          return new IncompatibleDeviceException(cause);
        } else {
          return new IncompatibleDeviceException("");
        }
      }
    }
  }

}
