/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.build.bundletool.validation;

/** Utility class to validate hex certificate digests. */
class FingerprintDigestValidator {

  private static final String HEX_FINGERPRINT_REGEX = "[0-9A-F]{2}(:[0-9A-F]{2}){31}";

  static boolean isValidFingerprintDigest(String digest) {
    return digest.matches(HEX_FINGERPRINT_REGEX);
  }

  private FingerprintDigestValidator() {}
}
