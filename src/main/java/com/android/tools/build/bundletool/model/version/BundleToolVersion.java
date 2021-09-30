/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.bundle.Config.BundleConfig;
import com.google.common.base.Strings;

/**
 * Version of BundleTool.
 *
 * <p>Matches the pattern "<major>.<minor>.<revision>[-<qualifier>]", e.g. "1.1.0" or
 * "1.2.1-alpha03".
 */
public final class BundleToolVersion {

  private static final String CURRENT_VERSION = "1.8.1";

  /** Returns the version of BundleTool being run. */
  public static Version getCurrentVersion() {
    return Version.of(CURRENT_VERSION);
  }

  public static Version getVersionFromBundleConfig(BundleConfig bundleConfig) {
    String rawVersion = bundleConfig.getBundletool().getVersion();

    // Legacy. Remove soon and fail.
    if (Strings.isNullOrEmpty(rawVersion)) {
      return Version.of("0.0.0");
    }

    return Version.of(rawVersion);
  }

  private BundleToolVersion() {}
}
