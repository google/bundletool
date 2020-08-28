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

import com.android.bundle.Errors.BundleToolError.ErrorType;

/** Indicates bundle with invalid version code (another error type for Play Console) */
public class InvalidVersionCodeException extends InvalidBundleException {

  private InvalidVersionCodeException(String message) {
    super(ErrorType.INVALID_VERSION_CODE_ERROR, message, message, null);
  }

  public static InvalidVersionCodeException createMissingVersionCodeException() {
    return new InvalidVersionCodeException("Version code not found in manifest.");
  }
}
