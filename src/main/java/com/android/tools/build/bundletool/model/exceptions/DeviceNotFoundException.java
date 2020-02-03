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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Error manifesting that a given device cannot be found. */
public class DeviceNotFoundException extends RuntimeException {

  @FormatMethod
  public DeviceNotFoundException(@FormatString String message, Object... args) {
    super(String.format(checkNotNull(message), args));
  }

  /** Thrown when we expected one device match but failed to achieve it. */
  public static class TooManyDevicesMatchedException extends DeviceNotFoundException {

    private final int matchedNumber;

    public int getMatchedNumber() {
      return matchedNumber;
    }

    public TooManyDevicesMatchedException(int matchedNumber) {
      super(
          "Unable to find one device matching the given criteria. Matched %d devices.",
          matchedNumber);
      this.matchedNumber = matchedNumber;
    }
  }

}
