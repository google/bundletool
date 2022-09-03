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
package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_R_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_T_API_VERSION;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkRuntimeVariantTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.variantMinSdkTargeting;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk;
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkRuntimeVariantGeneratorTest {

  private static final RuntimeEnabledSdkConfig RUNTIME_ENABLED_SDK_CONFIG =
      RuntimeEnabledSdkConfig.newBuilder()
          .addRuntimeEnabledSdk(
              RuntimeEnabledSdk.newBuilder()
                  .setPackageName("com.test.sdk")
                  .setVersionMajor(1)
                  .setCertificateDigest("AA:BB:CC"))
          .build();
  private static final BundleModule MODULE =
      new BundleModuleBuilder("base").setManifest(androidManifest("com.test.app")).build();
  private static final AppBundle APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS =
      new AppBundleBuilder()
          .addModule(
              MODULE.toBuilder().setRuntimeEnabledSdkConfig(RUNTIME_ENABLED_SDK_CONFIG).build())
          .build();
  private static final AppBundle APP_BUNDLE_WITHOUT_RUNTIME_ENABLED_SDK_DEPS =
      new AppBundleBuilder().addModule(MODULE).build();

  @Test
  public void bundleHasRuntimeEnabledSdkDeps_generatesSdkRuntimeVariant() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(/* sdkVersionVariantTargetings= */ ImmutableSet.of());

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(
                ANDROID_T_API_VERSION, /* alternativeSdkVersions= */ ImmutableSet.of()));
  }

  @Test
  public void bundleDoesNotHaveRuntimeEnabledSdkDeps_doesNotGenerateSdkRuntimeVariant() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITHOUT_RUNTIME_ENABLED_SDK_DEPS)
            .generate(/* sdkVersionVariantTargetings= */ ImmutableSet.of());

    assertThat(sdkRuntimeVariantTargetings).isEmpty();
  }

  @Test
  public void noOtherVariantTargetings_generatesOneVariant() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(/* sdkVersionVariantTargetings= */ ImmutableSet.of());

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(
                ANDROID_T_API_VERSION, /* alternativeSdkVersions= */ ImmutableSet.of()));
  }

  @Test
  public void otherVariantTargetingsTargetPreT_generatesOneVariant() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(
                ImmutableSet.of(
                    variantMinSdkTargeting(ANDROID_R_API_VERSION),
                    variantMinSdkTargeting(ANDROID_S_API_VERSION)));

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(
                ANDROID_T_API_VERSION, /* alternativeSdkVersions= */ ImmutableSet.of()));
  }

  @Test
  public void otherVariantTargetingTargetsT_generatesOneVariant() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(ImmutableSet.of(variantMinSdkTargeting(ANDROID_T_API_VERSION)));

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(
                ANDROID_T_API_VERSION, /* alternativeSdkVersions= */ ImmutableSet.of()));
  }

  @Test
  public void otherVariantTargetingsTargetPostT_generatesOneForEachAndT() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(
                ImmutableSet.of(
                    variantMinSdkTargeting(ANDROID_T_API_VERSION + 1),
                    variantMinSdkTargeting(ANDROID_T_API_VERSION + 2),
                    variantMinSdkTargeting(ANDROID_T_API_VERSION + 3)));

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION),
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION + 1),
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION + 2),
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION + 3));
  }

  @Test
  public void otherVariantTargetingsTargetPreAndPostT_generatesOneForPostTAndT() {
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        new SdkRuntimeVariantGenerator(APP_BUNDLE_WITH_RUNTIME_ENABLED_SDK_DEPS)
            .generate(
                ImmutableSet.of(
                    variantMinSdkTargeting(ANDROID_S_API_VERSION),
                    variantMinSdkTargeting(ANDROID_T_API_VERSION + 1)));

    assertThat(sdkRuntimeVariantTargetings)
        .containsExactly(
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION),
            sdkRuntimeVariantTargeting(ANDROID_T_API_VERSION + 1));
  }
}
