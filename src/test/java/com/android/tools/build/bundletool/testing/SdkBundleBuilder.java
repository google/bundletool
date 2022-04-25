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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.testing.TestUtils.createSdkAndroidManifest;

import com.android.bundle.Config.Bundletool;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import java.util.zip.ZipFile;

/**
 * Builder of {@link SdkBundle} instances in tests.
 *
 * <p>Note that the SdkBundle is created in-memory. To test an SdkBundle created from a ZIP file,
 * use {@link com.android.tools.build.bundletool.io.ZipBuilder} then call {@link
 * SdkBundle#buildFromZip(ZipFile, ZipFile, Integer)}.
 */
public class SdkBundleBuilder {

  public static final SdkModulesConfig DEFAULT_SDK_MODULES_CONFIG = getSdkModulesConfig();

  public static final String PACKAGE_NAME = "com.test.sdk";

  private static final BundleMetadata METADATA = BundleMetadata.builder().build();

  private BundleModule module = defaultModule();

  private Integer versionCode = 1;

  private SdkModulesConfig sdkModulesConfig = DEFAULT_SDK_MODULES_CONFIG;

  private static BundleModule defaultModule() {
    return new BundleModuleBuilder("base").setManifest(createSdkAndroidManifest()).build();
  }

  public SdkBundleBuilder setModule(BundleModule module) {
    this.module = module;
    return this;
  }

  public SdkBundleBuilder setVersionCode(Integer versionCode) {
    this.versionCode = versionCode;
    return this;
  }

  public SdkBundleBuilder setSdkModulesConfig(
      String bundletoolVersion, String packageName, int major, int minor, int patch) {
    this.sdkModulesConfig =
        createSdkModulesConfig(bundletoolVersion, packageName, major, minor, patch);
    return this;
  }

  public static SdkModulesConfig createSdkModulesConfig(
      String bundletoolVersion, String packageName, int major, int minor, int patch) {
    return SdkModulesConfig.newBuilder()
        .setSdkPackageName(packageName)
        .setBundletool(Bundletool.newBuilder().setVersion(bundletoolVersion))
        .setSdkVersion(
            RuntimeEnabledSdkVersion.newBuilder().setMajor(major).setMinor(minor).setPatch(patch))
        .build();
  }

  public SdkBundle build() {
    return SdkBundle.builder()
        .setModule(module)
        .setSdkModulesConfig(sdkModulesConfig)
        .setBundleMetadata(METADATA)
        .setVersionCode(versionCode)
        .build();
  }

  private static SdkModulesConfig getSdkModulesConfig() {
    return SdkModulesConfig.newBuilder()
        .setSdkPackageName(PACKAGE_NAME)
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
        .build();
  }
}
