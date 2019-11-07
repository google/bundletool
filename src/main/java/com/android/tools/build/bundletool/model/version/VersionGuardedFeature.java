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
package com.android.tools.build.bundletool.model.version;

/** Features that are enabled only from a certain Bundletool version. */
public enum VersionGuardedFeature {
  /**
   * When an APK has minSdkVersion>=24, we should be able to sign only with v2 signing, since the v2
   * signing scheme was introduced in Android N. This reduces the size of apps by removing a few
   * files under META-INF.
   */
  NO_V1_SIGNING_WHEN_POSSIBLE("0.11.0");

  /** Version from which the given feature should be enabled by default. */
  private final Version enabledSinceVersion;

  VersionGuardedFeature(String enabledSinceVersion) {
    this.enabledSinceVersion = Version.of(enabledSinceVersion);
  }

  /** Whether the feature should be enabled if the bundle was built with the given version. */
  public boolean enabledForVersion(Version bundletoolVersion) {
    return !enabledSinceVersion.isNewerThan(bundletoolVersion);
  }
}
