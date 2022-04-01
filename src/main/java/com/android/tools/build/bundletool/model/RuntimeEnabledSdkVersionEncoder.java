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
package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utility class for encoding runtime-enabled SDK major and minor version into a single value, to be
 * set in the APK manifest.
 *
 * <p>SDK version code in the APK manifest will be expressed by 'android:versionMajor' value, which
 * should contain both: SDK major version and SDK minor version. In order to have two numbers in
 * one, we split the 32-bit space into two: 260,000 major versions (~18 bits) and 10,000 minor
 * versions (~14 bits). For example, major 1 and minor 2 are represented as 10,002 in the
 * 'android:versionMajor' identifier.
 */
public final class RuntimeEnabledSdkVersionEncoder {

  @VisibleForTesting static final int VERSION_MAJOR_OFFSET = 10000;

  /** SDKs can publish 200,000 different major versions with values between [0, 200,000). */
  public static final int VERSION_MAJOR_MAX_VALUE = 199999;

  /** Each major version can have 10,000 minor versions with values between [0, 10,000). */
  public static final int VERSION_MINOR_MAX_VALUE = VERSION_MAJOR_OFFSET - 1;

  /**
   * Encodes SDK major and minor version into a single {@code long}, to be used as the value of
   * android:versionMajor attribute of <sdk-library> and <uses-sdk-library> tags of the APK
   * manifest.
   */
  public static int encodeSdkMajorAndMinorVersion(int versionMajor, int versionMinor) {
    checkArgument(
        versionMajor >= 0 && versionMajor <= VERSION_MAJOR_MAX_VALUE,
        "SDK major version must be an integer between 0 and " + VERSION_MAJOR_MAX_VALUE);
    checkArgument(
        versionMinor >= 0 && versionMinor <= VERSION_MINOR_MAX_VALUE,
        "SDK minor version must be an integer between 0 and " + VERSION_MINOR_MAX_VALUE);
    return VERSION_MAJOR_OFFSET * versionMajor + versionMinor;
  }

  private RuntimeEnabledSdkVersionEncoder() {}
}
