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
import com.android.bundle.SdkBundleConfigProto.SdkBundleConfig;
import com.android.bundle.SdkModulesConfigOuterClass.RuntimeEnabledSdkVersion;
import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.io.ByteSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
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

  public static final SdkBundleConfig DEFAULT_SDK_BUNDLE_CONFIG = getSdkBundleConfig();

  public static final String PACKAGE_NAME = "com.test.sdk";

  private static final String SDK_PROVIDER_CLASS_NAME =
      "com.example.sandboxservice.MyAdsSdkEntryPoint";

  private static final BundleMetadata METADATA = BundleMetadata.builder().build();

  private BundleModule module = defaultModule();

  private Integer versionCode = 1;

  private SdkModulesConfig sdkModulesConfig = DEFAULT_SDK_MODULES_CONFIG;

  private SdkBundleConfig sdkBundleConfig = DEFAULT_SDK_BUNDLE_CONFIG;

  private Optional<ByteSource> sdkInterfaceDescriptors = Optional.empty();

  private static BundleModule defaultModule() {
    return new BundleModuleBuilder("base").setManifest(createSdkAndroidManifest()).build();
  }

  @CanIgnoreReturnValue
  public SdkBundleBuilder setModule(BundleModule module) {
    this.module = module;
    return this;
  }

  @CanIgnoreReturnValue
  public SdkBundleBuilder setVersionCode(Integer versionCode) {
    this.versionCode = versionCode;
    return this;
  }

  @CanIgnoreReturnValue
  public SdkBundleBuilder setSdkModulesConfig(SdkModulesConfig sdkModulesConfig) {
    this.sdkModulesConfig = sdkModulesConfig;
    return this;
  }

  @CanIgnoreReturnValue
  public SdkBundleBuilder setSdkBundleConfig(SdkBundleConfig sdkBundleConfig) {
    this.sdkBundleConfig = sdkBundleConfig;
    return this;
  }

  @CanIgnoreReturnValue
  public SdkBundleBuilder setSdkInterfaceDescriptors(ByteSource sdkInterfaceDescriptors) {
    this.sdkInterfaceDescriptors = Optional.of(sdkInterfaceDescriptors);
    return this;
  }

  /** Creates an {@link SdkModulesConfig.Builder } with example default values. */
  public static SdkModulesConfig.Builder createSdkModulesConfig() {
    return SdkModulesConfig.newBuilder()
        .setSdkPackageName(PACKAGE_NAME)
        .setSdkProviderClassName(SDK_PROVIDER_CLASS_NAME)
        .setSdkVersion(sdkVersionBuilder())
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()));
  }

  public static RuntimeEnabledSdkVersion.Builder sdkVersionBuilder() {
    return RuntimeEnabledSdkVersion.newBuilder().setMajor(1).setMinor(1).setPatch(1);
  }

  public SdkBundle build() {
    SdkBundle.Builder sdkBundle =
        SdkBundle.builder()
            .setModule(module)
            .setSdkModulesConfig(sdkModulesConfig)
            .setSdkBundleConfig(sdkBundleConfig)
            .setBundleMetadata(METADATA)
            .setVersionCode(versionCode);
    sdkInterfaceDescriptors.ifPresent(sdkBundle::setSdkInterfaceDescriptors);
    return sdkBundle.build();
  }

  private static SdkModulesConfig getSdkModulesConfig() {
    return createSdkModulesConfig().build();
  }

  private static SdkBundleConfig getSdkBundleConfig() {
    return SdkBundleConfig.getDefaultInstance();
  }
}
