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

import static com.android.tools.build.bundletool.model.utils.ResultUtils.isStandaloneApkVariant;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.device.ApkMatcher.GeneratedApk;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Get total size (min and max) for variant, for each {@link SizeConfiguration} based on the
 * requested dimensions passed to {@link GetSizeCommand}.
 */
public class VariantTotalSizeAggregator extends AbstractSizeAggregator {

  private final Version bundleVersion;
  private final Variant variant;

  public VariantTotalSizeAggregator(
      ImmutableMap<String, Long> sizeByApkPaths,
      Version bundleVersion,
      Variant variant,
      GetSizeRequest getSizeRequest) {
    super(sizeByApkPaths, getSizeRequest);
    this.bundleVersion = bundleVersion;
    this.variant = variant;
  }

  @Override
  public ConfigurationSizes getSize() {
    if (isStandaloneApkVariant(variant)) {
      return getSizeStandaloneVariant();
    } else {
      return getSizeNonStandaloneVariant();
    }
  }

  @Override
  protected ImmutableList<GeneratedApk> getMatchingApks(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureTargeting,
      DeviceTierTargeting deviceTierTargeting,
      CountrySetTargeting countrySetTargeting,
      SdkRuntimeTargeting sdkRuntimeTargeting) {
    return new ApkMatcher(
            getDeviceSpec(
                getSizeRequest.getDeviceSpec(),
                sdkVersionTargeting,
                abiTargeting,
                screenDensityTargeting,
                languageTargeting,
                textureTargeting,
                deviceTierTargeting,
                countrySetTargeting,
                sdkRuntimeTargeting),
            getSizeRequest.getModules(),
            /* includeInstallTimeAssetModules= */ false,
            getSizeRequest.getInstant(),
            /* ensureDensityAndAbiApksMatched= */ false)
        .getMatchingApksFromVariant(variant, bundleVersion);
  }

  private ConfigurationSizes getSizeNonStandaloneVariant() {
    ImmutableList<ApkDescription> apkDescriptions =
        variant.getApkSetList().stream()
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
            .collect(toImmutableList());

    ImmutableSet<SdkVersionTargeting> sdkVersionTargetingOptions =
        ImmutableSet.of(variant.getTargeting().getSdkVersionTargeting());
    ImmutableSet<AbiTargeting> abiTargetingOptions = getAllAbiTargetings(apkDescriptions);
    ImmutableSet<LanguageTargeting> languageTargetingOptions =
        getAllLanguageTargetings(apkDescriptions);
    ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions =
        getAllScreenDensityTargetings(apkDescriptions);
    ImmutableSet<TextureCompressionFormatTargeting> textureCompressionFormatTargetingOptions =
        getAllTextureCompressionFormatTargetings(apkDescriptions);
    ImmutableSet<DeviceTierTargeting> deviceTierTargetingOptions =
        getAllDeviceTierTargetings(apkDescriptions);
    ImmutableSet<CountrySetTargeting> countrySetTargetingOptions =
        getAllCountrySetTargetings(apkDescriptions);

    return getSizesPerConfiguration(
        sdkVersionTargetingOptions,
        abiTargetingOptions,
        languageTargetingOptions,
        screenDensityTargetingOptions,
        textureCompressionFormatTargetingOptions,
        deviceTierTargetingOptions,
        countrySetTargetingOptions,
        variant.getTargeting().getSdkRuntimeTargeting());
  }

  private ConfigurationSizes getSizeStandaloneVariant() {
    checkState(
        !getSizeRequest.getInstant(),
        "Standalone Variants can't be selected when instant flag is set");

    // When modules are specified we ignore standalone variants.
    if (getSizeRequest.getModules().isPresent()) {
      return ConfigurationSizes.create(ImmutableMap.of(), ImmutableMap.of());
    }

    VariantTargeting variantTargeting = variant.getTargeting();

    SizeConfiguration sizeConfiguration =
        mergeWithDeviceSpec(
            getSizeConfiguration(
                variantTargeting.getSdkVersionTargeting(),
                variantTargeting.getAbiTargeting(),
                variantTargeting.getScreenDensityTargeting(),
                LanguageTargeting.getDefaultInstance(),
                variantTargeting.getTextureCompressionFormatTargeting(),
                DeviceTierTargeting.getDefaultInstance(),
                CountrySetTargeting.getDefaultInstance(),
                variantTargeting.getSdkRuntimeTargeting()),
            getSizeRequest.getDeviceSpec());

    // Variants of standalone APKs have only one APK each.
    long compressedSize =
        sizeByApkPaths.get(
            Iterables.getOnlyElement(
                    Iterables.getOnlyElement(variant.getApkSetList()).getApkDescriptionList())
                .getPath());

    ImmutableMap<SizeConfiguration, Long> sizeConfigurationMap =
        ImmutableMap.of(sizeConfiguration, compressedSize);
    return ConfigurationSizes.create(sizeConfigurationMap, sizeConfigurationMap);
  }
}
