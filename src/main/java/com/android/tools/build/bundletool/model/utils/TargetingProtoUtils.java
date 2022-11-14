/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.protobuf.Int32Value;
import java.util.Optional;

/** Utility functions for Targeting proto. */
public final class TargetingProtoUtils {

  /** Moves targeting values to the alternatives. */
  public static AssetsDirectoryTargeting toAlternativeTargeting(
      AssetsDirectoryTargeting targeting) {
    AssetsDirectoryTargeting.Builder alternativeTargeting = AssetsDirectoryTargeting.newBuilder();
    if (targeting.hasTextureCompressionFormat()) {
      alternativeTargeting
          .getTextureCompressionFormatBuilder()
          .addAllAlternatives(targeting.getTextureCompressionFormat().getValueList());
    }
    if (targeting.hasAbi()) {
      alternativeTargeting.getAbiBuilder().addAllAlternatives(targeting.getAbi().getValueList());
    }
    if (targeting.hasLanguage()) {
      alternativeTargeting
          .getLanguageBuilder()
          .addAllAlternatives(targeting.getLanguage().getValueList());
    }
    if (targeting.hasDeviceTier()) {
      alternativeTargeting
          .getDeviceTierBuilder()
          .addAllAlternatives(targeting.getDeviceTier().getValueList());
    }
    if (targeting.hasCountrySet()) {
      alternativeTargeting
          .getCountrySetBuilder()
          .addAllAlternatives(targeting.getCountrySet().getValueList());
    }
    return alternativeTargeting.build();
  }

  /** Extracts multi-ABI values from the targeting. */
  public static ImmutableSet<MultiAbi> multiAbiValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getMultiAbiTargeting().getValueList());
  }

  /** Extracts multi-ABI alternatives from the targeting. */
  public static ImmutableSet<MultiAbi> multiAbiAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getMultiAbiTargeting().getAlternativesList());
  }

  /** Extracts targeted multi-ABI universe (values and alternatives) from the targeting. */
  public static ImmutableSet<MultiAbi> multiAbiUniverse(ApkTargeting targeting) {
    return ImmutableSet.<MultiAbi>builder()
        .addAll(multiAbiValues(targeting))
        .addAll(multiAbiAlternatives(targeting))
        .build();
  }

  /** Extracts ABI values from the targeting. */
  public static ImmutableSet<Abi> abiValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getAbiTargeting().getValueList());
  }

  /** Extracts ABI alternatives from the targeting. */
  public static ImmutableSet<Abi> abiAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getAbiTargeting().getAlternativesList());
  }

  /** Extracts targeted ABI universe (values and alternatives) from the targeting. */
  public static ImmutableSet<Abi> abiUniverse(ApkTargeting targeting) {
    return ImmutableSet.<Abi>builder()
        .addAll(abiValues(targeting))
        .addAll(abiAlternatives(targeting))
        .build();
  }

  /** Extracts screen density values from the targeting. */
  public static ImmutableSet<ScreenDensity> densityValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getScreenDensityTargeting().getValueList());
  }

  /** Extracts screen density alternatives from the targeting. */
  public static ImmutableSet<ScreenDensity> densityAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getScreenDensityTargeting().getAlternativesList());
  }

  /** Extracts targeted screen density universe (values and alternatives) from the targeting. */
  public static ImmutableSet<ScreenDensity> densityUniverse(ApkTargeting targeting) {
    return ImmutableSet.<ScreenDensity>builder()
        .addAll(densityValues(targeting))
        .addAll(densityAlternatives(targeting))
        .build();
  }

  /** Extracts language values from the targeting. */
  public static ImmutableSet<String> languageValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getLanguageTargeting().getValueList());
  }

  /** Extracts language alternatives from the targeting. */
  public static ImmutableSet<String> languageAlternatives(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getLanguageTargeting().getAlternativesList());
  }

  /** Extracts targeted language universe (values and alternatives) from the targeting. */
  public static ImmutableSet<String> languageUniverse(ApkTargeting targeting) {
    return ImmutableSet.<String>builder()
        .addAll(languageValues(targeting))
        .addAll(languageAlternatives(targeting))
        .build();
  }

  /** Extracts Texture Compression Format values from the targeting. */
  public static ImmutableSet<TextureCompressionFormat> textureCompressionFormatValues(
      ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getTextureCompressionFormatTargeting().getValueList());
  }

  /** Extracts Texture Compression Format alternatives from the targeting. */
  public static ImmutableSet<TextureCompressionFormat> textureCompressionFormatAlternatives(
      ApkTargeting targeting) {
    return ImmutableSet.copyOf(
        targeting.getTextureCompressionFormatTargeting().getAlternativesList());
  }

  /**
   * Extracts targeted Texture Compression Format universe (values and alternatives) from the
   * targeting.
   */
  public static ImmutableSet<TextureCompressionFormat> textureCompressionFormatUniverse(
      ApkTargeting targeting) {
    return ImmutableSet.<TextureCompressionFormat>builder()
        .addAll(textureCompressionFormatValues(targeting))
        .addAll(textureCompressionFormatAlternatives(targeting))
        .build();
  }

  /** Extracts Texture Compression Format values from the targeting. */
  public static ImmutableSet<Integer> deviceTierValues(ApkTargeting targeting) {
    return targeting.getDeviceTierTargeting().getValueList().stream()
        .map(Int32Value::getValue)
        .collect(toImmutableSet());
  }

  /** Extracts targeted Device Tier universe (values and alternatives) from the targeting. */
  public static ImmutableSet<Integer> deviceTierUniverse(ApkTargeting targeting) {
    return Streams.concat(
            targeting.getDeviceTierTargeting().getValueList().stream(),
            targeting.getDeviceTierTargeting().getAlternativesList().stream())
        .map(Int32Value::getValue)
        .collect(toImmutableSet());
  }

  public static ImmutableSet<String> countrySetUniverse(ApkTargeting targeting) {
    return Streams.concat(
            targeting.getCountrySetTargeting().getValueList().stream(),
            targeting.getCountrySetTargeting().getAlternativesList().stream())
        .collect(toImmutableSet());
  }

  public static ImmutableSet<String> countrySetValues(ApkTargeting targeting) {
    return ImmutableSet.copyOf(targeting.getCountrySetTargeting().getValueList());
  }

  public static SdkVersion sdkVersionFrom(int from) {
    return SdkVersion.newBuilder().setMin(Int32Value.newBuilder().setValue(from)).build();
  }

  public static SdkVersionTargeting sdkVersionTargeting(
      SdkVersion sdkVersion, ImmutableSet<SdkVersion> alternatives) {
    return SdkVersionTargeting.newBuilder()
        .addValue(sdkVersion)
        .addAllAlternatives(alternatives)
        .build();
  }

  public static SdkVersionTargeting sdkVersionTargeting(SdkVersion sdkVersion) {
    return SdkVersionTargeting.newBuilder().addValue(sdkVersion).build();
  }

  public static VariantTargeting variantTargeting(SdkVersionTargeting sdkVersionTargeting) {
    return VariantTargeting.newBuilder().setSdkVersionTargeting(sdkVersionTargeting).build();
  }

  public static VariantTargeting lPlusVariantTargeting() {
    return variantTargeting(sdkVersionTargeting(sdkVersionFrom(ANDROID_L_API_VERSION)));
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(SdkVersion sdkVersion) {
    return sdkRuntimeVariantTargeting(sdkVersion, /* alternativeSdkVersions= */ ImmutableSet.of());
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(
      SdkVersion sdkVersion, ImmutableSet<SdkVersion> alternativeSdkVersions) {
    return VariantTargeting.newBuilder()
        .setSdkRuntimeTargeting(SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder()
                .addValue(sdkVersion)
                .addAllAlternatives(alternativeSdkVersions))
        .build();
  }

  public static Optional<Integer> getScreenDensityDpi(
      ScreenDensityTargeting screenDensityTargeting) {
    if (screenDensityTargeting.getValueList().isEmpty()) {
      return Optional.empty();
    }

    ScreenDensity densityTargeting =
        screenDensityTargeting.getValueList().stream()
            // For now we only support one value in ScreenDensityTargeting.
            .collect(onlyElement());
    return Optional.of(ResourcesUtils.convertToDpi(densityTargeting));
  }
}
