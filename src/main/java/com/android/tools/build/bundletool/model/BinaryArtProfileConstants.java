/*
 * Copyright (C) 2023 The Android Open Source Project
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

/** Constants related to baseline profiles. */
public final class BinaryArtProfileConstants {

  /**
   * Location for baseline profiles in an APK.
   *
   * <p>This is the location where Jetpack Profile Installer will fetch them from, to install
   * baseline profiles on devices where dex metadata files are missing at install-time.
   */
  public static final String PROFILE_APK_LOCATION = "assets/dexopt";

  /**
   * Subfolder of the bundle's {@code BUNDLE-METADATA} folder where baseline profiles are located.
   */
  public static final String PROFILE_METADATA_NAMESPACE = "com.android.tools.build.profiles";

  /**
   * Legacy subfolder of the bundle's {@code BUNDLE-METADATA} folder where baseline profiles used to
   * be located.
   */
  public static final String LEGACY_PROFILE_METADATA_NAMESPACE = "assets.dexopt";

  /** Name of the baseline profile file. */
  public static final String PROFILE_FILENAME = "baseline.prof";

  /** Name of the baseline profile metadata file. */
  public static final String PROFILE_METADATA_FILENAME = "baseline.profm";

  private BinaryArtProfileConstants() {}
}
