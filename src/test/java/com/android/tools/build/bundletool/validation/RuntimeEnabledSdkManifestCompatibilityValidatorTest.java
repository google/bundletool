/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withTargetSdkVersion;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.testing.AppBundleBuilder;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RuntimeEnabledSdkManifestCompatibilityValidatorTest {

  @Test
  public void validateBundleWithSdkModules_appMinSdkLowerThanSdkMinSdk_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app", withMinSdkVersion(31), withTargetSdkVersion(35))))
            .build();
    BundleModule sdkModule =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(32), withTargetSdkVersion(35)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkManifestCompatibilityValidator()
                    .validateBundleWithSdkModules(appBundle, ImmutableMap.of("sdk1", sdkModule)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Runtime-enabled SDKs must not have a minSdkVersion greater than the app, but found SDK"
                + " 'sdk1' with minSdkVersion (32) higher than the app's minSdkVersion (31).");
  }

  @Test
  public void validateBundleWithSdkModules_appMinSdkLowerThanAtLeastOneSdkMinSdk_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app", withMinSdkVersion(31), withTargetSdkVersion(35))))
            .build();
    BundleModule sdkModule1 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(30), withTargetSdkVersion(35)))
            .build();
    BundleModule sdkModule2 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(32), withTargetSdkVersion(35)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkManifestCompatibilityValidator()
                    .validateBundleWithSdkModules(
                        appBundle, ImmutableMap.of("sdk1", sdkModule1, "sdk2", sdkModule2)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Runtime-enabled SDKs must not have a minSdkVersion greater than the app, but found SDK"
                + " 'sdk2' with minSdkVersion (32) higher than the app's minSdkVersion (31).");
  }

  @Test
  public void validateBundleWithSdkModules_targetSdkLowerThanMinSdkOfAnotherSdk_throws() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app", withMinSdkVersion(32), withTargetSdkVersion(35))))
            .build();
    BundleModule sdkModule1 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(30), withTargetSdkVersion(31)))
            .build();
    BundleModule sdkModule2 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(32), withTargetSdkVersion(35)))
            .build();

    InvalidBundleException exception =
        assertThrows(
            InvalidBundleException.class,
            () ->
                new RuntimeEnabledSdkManifestCompatibilityValidator()
                    .validateBundleWithSdkModules(
                        appBundle, ImmutableMap.of("sdk1", sdkModule1, "sdk2", sdkModule2)));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Runtime-enabled SDKs must have a minSdkVersion lower or equal to the targetSdkVersion"
                + " of another SDK, but found SDK 'sdk2' with minSdkVersion (32) higher than the"
                + " targetSdkVersion (31) of SDK 'sdk1'.");
  }

  @Test
  public void validateBundleWithSdkModules_ok() {
    AppBundle appBundle =
        new AppBundleBuilder()
            .addModule(
                "base",
                module ->
                    module.setManifest(
                        androidManifest(
                            "com.app", withMinSdkVersion(31), withTargetSdkVersion(35))))
            .build();
    BundleModule sdkModule1 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(30), withTargetSdkVersion(35)))
            .build();
    BundleModule sdkModule2 =
        new BundleModuleBuilder("base")
            .setManifest(
                androidManifest("com.test.sdk", withMinSdkVersion(31), withTargetSdkVersion(35)))
            .build();

    // No exception thrown.
    new RuntimeEnabledSdkManifestCompatibilityValidator()
        .validateBundleWithSdkModules(
            appBundle, ImmutableMap.of("sdk1", sdkModule1, "sdk2", sdkModule2));
  }
}
