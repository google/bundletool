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

package com.android.tools.build.bundletool.model.utils;

import static com.google.common.collect.Comparators.lexicographical;
import static com.google.common.collect.ImmutableList.sortedCopyOf;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.Sanitizer;
import com.android.bundle.Targeting.SanitizerTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Int32Value;
import java.util.Collection;
import java.util.Comparator;

/** Helpers related to APK resources qualifiers. */
public final class TargetingNormalizer {

  private static final Comparator<Abi> ABI_COMPARATOR = comparing(Abi::getAlias);

  private static final Comparator<MultiAbi> MULTI_ABI_COMPARATOR =
      comparing(MultiAbi::getAbiList, lexicographical(comparing(Abi::getAlias)));

  private static final Comparator<Sanitizer> SANITIZER_COMPARATOR = comparing(Sanitizer::getAlias);

  private static final Comparator<ScreenDensity> SCREEN_DENSITY_COMPARATOR =
      Comparator.comparingInt(ResourcesUtils::convertToDpi);

  private static final Comparator<SdkVersion> SDK_VERSION_COMPARATOR =
      comparing(sdkVersion -> sdkVersion.getMin().getValue());

  private static final Comparator<TextureCompressionFormat> TEXTURE_COMPRESSION_FORMAT_COMPARATOR =
      comparing(TextureCompressionFormat::getAlias);

  public static ApkTargeting normalizeApkTargeting(ApkTargeting targeting) {
    ApkTargeting.Builder normalized = targeting.toBuilder();
    if (targeting.hasAbiTargeting()) {
      normalized.setAbiTargeting(normalizeAbiTargeting(targeting.getAbiTargeting()));
    }
    if (targeting.hasLanguageTargeting()) {
      normalized.setLanguageTargeting(normalizeLanguageTargeting(targeting.getLanguageTargeting()));
    }
    if (targeting.hasMultiAbiTargeting()) {
      normalized.setMultiAbiTargeting(normalizeMultiAbiTargeting(targeting.getMultiAbiTargeting()));
    }
    if (targeting.hasSanitizerTargeting()) {
      normalized.setSanitizerTargeting(
          normalizeSanitizerTargeting(targeting.getSanitizerTargeting()));
    }
    if (targeting.hasScreenDensityTargeting()) {
      normalized.setScreenDensityTargeting(
          normalizeScreenDensityTargeting(targeting.getScreenDensityTargeting()));
    }
    if (targeting.hasSdkVersionTargeting()) {
      normalized.setSdkVersionTargeting(
          normalizeSdkVersionTargeting(targeting.getSdkVersionTargeting()));
    }
    if (targeting.hasTextureCompressionFormatTargeting()) {
      normalized.setTextureCompressionFormatTargeting(
          normalizeTextureCompressionFormatTargeting(
              targeting.getTextureCompressionFormatTargeting()));
    }
    if (targeting.hasDeviceTierTargeting()) {
      normalized.setDeviceTierTargeting(
          normalizeDeviceTierTargeting(targeting.getDeviceTierTargeting()));
    }
    if (targeting.hasCountrySetTargeting()) {
      normalized.setCountrySetTargeting(
          normalizeCountrySetTargeting(targeting.getCountrySetTargeting()));
    }
    return normalized.build();
  }

  public static VariantTargeting normalizeVariantTargeting(VariantTargeting targeting) {
    VariantTargeting.Builder normalized = targeting.toBuilder();
    if (targeting.hasAbiTargeting()) {
      normalized.setAbiTargeting(normalizeAbiTargeting(targeting.getAbiTargeting()));
    }
    if (targeting.hasMultiAbiTargeting()) {
      normalized.setMultiAbiTargeting(normalizeMultiAbiTargeting(targeting.getMultiAbiTargeting()));
    }
    if (targeting.hasScreenDensityTargeting()) {
      normalized.setScreenDensityTargeting(
          normalizeScreenDensityTargeting(targeting.getScreenDensityTargeting()));
    }
    if (targeting.hasSdkVersionTargeting()) {
      normalized.setSdkVersionTargeting(
          normalizeSdkVersionTargeting(targeting.getSdkVersionTargeting()));
    }
    if (targeting.hasTextureCompressionFormatTargeting()) {
      normalized.setTextureCompressionFormatTargeting(
          normalizeTextureCompressionFormatTargeting(
              targeting.getTextureCompressionFormatTargeting()));
    }
    return normalized.build();
  }

  private static AbiTargeting normalizeAbiTargeting(AbiTargeting targeting) {
    return AbiTargeting.newBuilder()
        .addAllValue(ImmutableList.sortedCopyOf(ABI_COMPARATOR, targeting.getValueList()))
        .addAllAlternatives(
            ImmutableList.sortedCopyOf(ABI_COMPARATOR, targeting.getAlternativesList()))
        .build();
  }

  private static LanguageTargeting normalizeLanguageTargeting(LanguageTargeting targeting) {
    return LanguageTargeting.newBuilder()
        .addAllValue(ImmutableList.sortedCopyOf(targeting.getValueList()))
        .addAllAlternatives(ImmutableList.sortedCopyOf(targeting.getAlternativesList()))
        .build();
  }

  private static MultiAbiTargeting normalizeMultiAbiTargeting(MultiAbiTargeting targeting) {
    return MultiAbiTargeting.newBuilder()
        .addAllValue(
            targeting.getValueList().stream()
                .map(TargetingNormalizer::normalizeMultiAbi)
                .sorted(MULTI_ABI_COMPARATOR)
                .collect(toImmutableList()))
        .addAllAlternatives(
            targeting.getAlternativesList().stream()
                .map(TargetingNormalizer::normalizeMultiAbi)
                .sorted(MULTI_ABI_COMPARATOR)
                .collect(toImmutableList()))
        .build();
  }

  private static MultiAbi normalizeMultiAbi(MultiAbi targeting) {
    return MultiAbi.newBuilder()
        .addAllAbi(ImmutableList.sortedCopyOf(ABI_COMPARATOR, targeting.getAbiList()))
        .build();
  }

  private static SanitizerTargeting normalizeSanitizerTargeting(SanitizerTargeting targeting) {
    return SanitizerTargeting.newBuilder()
        .addAllValue(ImmutableList.sortedCopyOf(SANITIZER_COMPARATOR, targeting.getValueList()))
        .build();
  }

  private static ScreenDensityTargeting normalizeScreenDensityTargeting(
      ScreenDensityTargeting targeting) {
    return ScreenDensityTargeting.newBuilder()
        .addAllValue(
            ImmutableList.sortedCopyOf(SCREEN_DENSITY_COMPARATOR, targeting.getValueList()))
        .addAllAlternatives(
            ImmutableList.sortedCopyOf(SCREEN_DENSITY_COMPARATOR, targeting.getAlternativesList()))
        .build();
  }

  private static SdkVersionTargeting normalizeSdkVersionTargeting(SdkVersionTargeting targeting) {
    return SdkVersionTargeting.newBuilder()
        .addAllValue(ImmutableList.sortedCopyOf(SDK_VERSION_COMPARATOR, targeting.getValueList()))
        .addAllAlternatives(
            ImmutableList.sortedCopyOf(SDK_VERSION_COMPARATOR, targeting.getAlternativesList()))
        .build();
  }

  private static TextureCompressionFormatTargeting normalizeTextureCompressionFormatTargeting(
      TextureCompressionFormatTargeting targeting) {
    return TextureCompressionFormatTargeting.newBuilder()
        .addAllValue(
            ImmutableList.sortedCopyOf(
                TEXTURE_COMPRESSION_FORMAT_COMPARATOR, targeting.getValueList()))
        .addAllAlternatives(
            ImmutableList.sortedCopyOf(
                TEXTURE_COMPRESSION_FORMAT_COMPARATOR, targeting.getAlternativesList()))
        .build();
  }

  private static DeviceTierTargeting normalizeDeviceTierTargeting(DeviceTierTargeting targeting) {
    return DeviceTierTargeting.newBuilder()
        .addAllValue(sortInt32Values(targeting.getValueList()))
        .addAllAlternatives(sortInt32Values(targeting.getAlternativesList()))
        .build();
  }

  private static CountrySetTargeting normalizeCountrySetTargeting(CountrySetTargeting targeting) {
    return CountrySetTargeting.newBuilder()
        .addAllValue(sortedCopyOf(targeting.getValueList()))
        .addAllAlternatives(sortedCopyOf(targeting.getAlternativesList()))
        .build();
  }

  private static ImmutableList<Int32Value> sortInt32Values(Collection<Int32Value> values) {
    return values.stream()
        .map(Int32Value::getValue)
        .sorted()
        .map(Int32Value::of)
        .collect(toImmutableList());
  }

  private TargetingNormalizer() {}
}
