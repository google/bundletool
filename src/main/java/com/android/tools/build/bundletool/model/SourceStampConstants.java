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

/** Constants for Source Stamp */
public final class SourceStampConstants {

  public static final String STAMP_SOURCE_METADATA_KEY = "com.android.stamp.source";
  public static final String STAMP_TYPE_METADATA_KEY = "com.android.stamp.type";

  /** Type of stamp generated. */
  public enum StampType {
    // Stamp generated for APKs intended for distribution.
    STAMP_TYPE_DISTRIBUTION_APK,
    // Stamp generated for standalone APKs.
    STAMP_TYPE_STANDALONE_APK,
  }

  private SourceStampConstants() {}
}
