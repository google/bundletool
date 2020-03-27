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

  /** ABI sanitizer no longer applied. */
  ABI_SANITIZER_DISABLED("0.3.1"),

  /** The namespace on the "include" attribute in the AndroidManifest.xml is required. */
  NAMESPACE_ON_INCLUDE_ATTRIBUTE_REQUIRED("0.3.4"),

  /**
   * Resources that don't have dpi-alternatives can live in the master split instead of being
   * duplicated in each DPI-specific APK.
   */
  RESOURCES_WITH_NO_ALTERNATIVES_IN_MASTER_SPLIT("0.4.0"),

  /** The module title is now required. */
  MODULE_TITLE_VALIDATION_ENFORCED("0.4.3"),


  /**
   * No longer keep a list of file extensions that should remain uncompressed by default. This is
   * left to the build system to decide.
   */
  NO_DEFAULT_UNCOMPRESS_EXTENSIONS("0.7.3"),

  /**
   * Move the resources referenced in the AndroidManifest.xml into the master split to ensure
   * Application.create() will be invoked even if other resources are missing. Added for the
   * Sideloading API.
   */
  RESOURCES_REFERENCED_IN_MANIFEST_TO_MASTER_SPLIT("0.8.1"),

  /**
   * Resources under "drawable-mdpi" take precedence over ones under "drawable". Although they
   * technically target the same screen density, developers are confused when we pick the other one.
   */
  PREFER_EXPLICIT_DPI_OVER_DEFAULT_CONFIG("0.9.1"),

  /** A new <delivery> tag replaces the now deprecated "onDemand" attribute. */
  NEW_DELIVERY_TYPE_MANIFEST_TAG("0.10.2"),

  /**
   * When an APK has minSdkVersion>=24, signing the APK only with v2 signing, since the v2 signing
   * scheme was introduced in Android N. This reduces the size of apps by removing a few files under
   * META-INF.
   */
  NO_V1_SIGNING_WHEN_POSSIBLE("0.11.0"),

  /**
   * Fuse activities from feature module AndroinManifest instead of relying on activities that are
   * merged from features to base module by Gradle plugin
   * (https://github.com/google/bundletool/issues/68).
   */
  FUSE_ACTIVITIES_FROM_FEATURE_MANIFESTS("0.13.4");

  /** Version from which the given feature should be enabled by default. */
  private final Version enabledSinceVersion;

  VersionGuardedFeature(String enabledSinceVersion) {
    this.enabledSinceVersion = Version.of(enabledSinceVersion);
  }

  /**
   * Whether the feature should be enabled if the bundle was built with the given version.
   *
   * @param bundletoolVersion The version of bundletool that was used to build the App Bundle.
   */
  public boolean enabledForVersion(Version bundletoolVersion) {
    return !bundletoolVersion.isOlderThan(enabledSinceVersion);
  }
}
