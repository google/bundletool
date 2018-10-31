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

package com.android.tools.build.bundletool.model;

import static com.android.bundle.Targeting.Abi.AbiAlias.ARM64_V8A;
import static com.android.bundle.Targeting.Abi.AbiAlias.ARMEABI_V7A;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86;
import static com.android.bundle.Targeting.Abi.AbiAlias.X86_64;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.HDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.LDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.MDPI;
import static com.android.bundle.Targeting.ScreenDensity.DensityAlias.XXHDPI;
import static com.android.tools.build.bundletool.model.GetSizeConfiguration.getAbiName;
import static com.android.tools.build.bundletool.model.GetSizeConfiguration.getLocaleName;
import static com.android.tools.build.bundletool.model.GetSizeConfiguration.getScreenDensityName;
import static com.android.tools.build.bundletool.model.GetSizeConfiguration.getSdkName;
import static com.android.tools.build.bundletool.model.GetSizeConfiguration.getSizeConfiguration;
import static com.android.tools.build.bundletool.testing.TargetingUtils.abiTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.languageTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.screenDensityTargeting;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.testing.TargetingUtils.sdkVersionTargeting;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GetSizeConfigurationTest {

  @Test
  public void getSdk_noAlternatives() {
    assertThat(getSdkName(SdkVersionTargeting.getDefaultInstance())).hasValue("1-");
    assertThat(getSdkName(sdkVersionTargeting(sdkVersionFrom(21)))).hasValue("21-");
  }

  @Test
  public void getSdk_withAlternatives() {
    assertThat(
            getSdkName(
                sdkVersionTargeting(sdkVersionFrom(21), ImmutableSet.of(sdkVersionFrom(23)))))
        .hasValue("21-22");
  }

  @Test
  public void getAbi_defaultAbiTargeting() {
    assertThat(getAbiName(AbiTargeting.getDefaultInstance())).isEmpty();
  }

  @Test
  public void getAbi_singleAbiTargeting() {
    assertThat(getAbiName(abiTargeting(ARMEABI_V7A))).hasValue("armeabi-v7a");
    assertThat(getAbiName(abiTargeting(X86_64))).hasValue("x86_64");
    assertThat(getAbiName(abiTargeting(X86))).hasValue("x86");
  }

  @Test
  public void getAbi_multipleAbiTargeting_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> getAbiName(abiTargeting(ImmutableSet.of(ARMEABI_V7A, X86_64), ImmutableSet.of())));
  }

  @Test
  public void getLocaleName_defaultLangTargeting() {
    assertThat(getLocaleName(LanguageTargeting.getDefaultInstance())).isEmpty();
  }

  @Test
  public void getLocaleName_singleLangTargeting() {
    assertThat(getLocaleName(languageTargeting("fr"))).hasValue("fr");
    assertThat(getLocaleName(languageTargeting("en-US"))).hasValue("en-US");
  }

  @Test
  public void getLocaleName_multipleLangTargeting_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> getLocaleName(languageTargeting("en", "fr")));
  }

  @Test
  public void getScreenDensity_defaultScreenDensity() {
    assertThat(getScreenDensityName(ScreenDensityTargeting.getDefaultInstance())).isEmpty();
  }

  @Test
  public void getScreenDensity_singleScreenDensity() {
    assertThat(getScreenDensityName(screenDensityTargeting(HDPI))).hasValue("HDPI");
    assertThat(getScreenDensityName(screenDensityTargeting(480, ImmutableSet.of(LDPI))))
        .hasValue("480");
  }

  @Test
  public void getScreenDensity_multipleScreenDensity_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            getScreenDensityName(
                screenDensityTargeting(ImmutableSet.of(HDPI, LDPI), ImmutableSet.of())));
  }

  @Test
  public void getSizeConfiguration_singleTargetings() {
    assertThat(
            getSizeConfiguration(
                Optional.empty(),
                Optional.of(abiTargeting(X86)),
                Optional.empty(),
                Optional.of(languageTargeting("en-UK"))))
        .isEqualTo(
            GetSizeConfiguration.builder()
                .setAbi(Optional.of("x86"))
                .setLocale(Optional.of("en-UK"))
                .build());

    assertThat(
            getSizeConfiguration(
                Optional.of(sdkVersionTargeting(sdkVersionFrom(25))),
                Optional.empty(),
                Optional.of(screenDensityTargeting(MDPI)),
                Optional.empty()))
        .isEqualTo(
            GetSizeConfiguration.builder()
                .setSdkVersion(Optional.of("25-"))
                .setScreenDensity(Optional.of("MDPI"))
                .build());

    assertThat(
            getSizeConfiguration(
                Optional.of(
                    sdkVersionTargeting(
                        sdkVersionFrom(25),
                        ImmutableSet.of(
                            sdkVersionFrom(21), sdkVersionFrom(32), sdkVersionFrom(28)))),
                Optional.of(abiTargeting(ARM64_V8A, ImmutableSet.of(ARMEABI_V7A))),
                Optional.of(screenDensityTargeting(252, ImmutableSet.of(LDPI, XXHDPI))),
                Optional.of(languageTargeting("jp"))))
        .isEqualTo(
            GetSizeConfiguration.builder()
                .setSdkVersion(Optional.of("25-27"))
                .setAbi(Optional.of("arm64-v8a"))
                .setScreenDensity(Optional.of("252"))
                .setLocale(Optional.of("jp"))
                .build());
  }

  @Test
  public void getSizeConfiguration_multipleTargetings_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            getSizeConfiguration(
                Optional.of(sdkVersionTargeting(sdkVersionFrom(25))),
                Optional.of(abiTargeting(ImmutableSet.of(ARMEABI_V7A, X86_64), ImmutableSet.of())),
                Optional.of(screenDensityTargeting(MDPI)),
                Optional.of(languageTargeting("en-UK"))));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            getSizeConfiguration(
                Optional.of(sdkVersionTargeting(sdkVersionFrom(25))),
                Optional.of(abiTargeting(X86_64)),
                Optional.of(screenDensityTargeting(ImmutableSet.of(HDPI, LDPI), ImmutableSet.of())),
                Optional.of(languageTargeting("en-UK"))));
  }
}
