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
package com.android.tools.build.bundletool.device;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXHDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXXHDPI;
import static com.android.tools.build.bundletool.testing.DeviceFactory.abis;
import static com.android.tools.build.bundletool.testing.DeviceFactory.density;
import static com.android.tools.build.bundletool.testing.DeviceFactory.locales;
import static com.android.tools.build.bundletool.testing.DeviceFactory.mergeSpecs;
import static com.android.tools.build.bundletool.testing.DeviceFactory.sdkVersion;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.screenDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.device.DeviceSpecUtils.DeviceSpecFromTargetingBuilder;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeviceSpecUtilsTest {

  @Test
  public void deviceSpecFromTargetingBuilder_setSdkVersion() {
    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSdkVersion(SdkVersionTargeting.getDefaultInstance())
                .build())
        .isEqualToDefaultInstance();

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSdkVersion(sdkVersionTargeting(sdkVersionFrom(25)))
                .build())
        .isEqualTo(sdkVersion(25));

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSdkVersion(
                    sdkVersionTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23))))
                .build())
        .isEqualTo(sdkVersion(21));
  }

  @Test
  public void deviceSpecFromTargetingBuilder_setSupportedAbis() {
    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSupportedAbis(AbiTargeting.getDefaultInstance())
                .build())
        .isEqualToDefaultInstance();

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSupportedAbis(abiTargeting(ARMEABI))
                .build())
        .isEqualTo(abis("armeabi"));

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSupportedAbis(abiTargeting(ARMEABI_V7A, ImmutableSet.of(X86)))
                .build())
        .isEqualTo(abis("armeabi-v7a"));
  }

  @Test
  public void deviceSpecFromTargetingBuilder_setScreenDensity() {
    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setScreenDensity(ScreenDensityTargeting.getDefaultInstance())
                .build())
        .isEqualToDefaultInstance();

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setScreenDensity(screenDensityTargeting(XXHDPI))
                .build())
        .isEqualTo(density(480));

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setScreenDensity(screenDensityTargeting(LDPI, ImmutableSet.of(MDPI)))
                .build())
        .isEqualTo(density(120));
  }

  @Test
  public void deviceSpecFromTargetingBuilder_setSupportedLocales() {
    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSupportedLocales(LanguageTargeting.getDefaultInstance())
                .build())
        .isEqualToDefaultInstance();

    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSupportedLocales(languageTargeting("fr"))
                .build())
        .isEqualTo(locales("fr"));
  }

  @Test
  public void deviceSpecFromTargetingBuilder_setMultipleTargetings() {
    assertThat(
            new DeviceSpecFromTargetingBuilder(DeviceSpec.getDefaultInstance())
                .setSdkVersion(
                    sdkVersionTargeting(sdkVersionFrom(25), ImmutableSet.of(sdkVersionFrom(23))))
                .setSupportedAbis(abiTargeting(X86_64, ImmutableSet.of(ARM64_V8A)))
                .setScreenDensity(screenDensityTargeting(XXXHDPI, ImmutableSet.of(XHDPI)))
                .setSupportedLocales(languageTargeting("fr"))
                .build())
        .isEqualTo(mergeSpecs(sdkVersion(25), abis("x86_64"), density(640), locales("fr")));
  }
}
