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

import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isAbiMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isLocalesMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isScreenDensityMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isSdkVersionMissing;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ABI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.model.utils.ResultUtils.isStandaloneApkVariant;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.device.DeviceSpecUtils.DeviceSpecFromTargetingBuilder;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest;
import com.android.tools.build.bundletool.model.GetSizeRequest.Dimension;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.HashMap;
import java.util.Map;

/**
 * Get total size (min and max) for variant, for each {@link SizeConfiguration} based on the
 * requested dimensions passed to {@link GetSizeCommand}.
 */
public class VariantTotalSizeAggregator {

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  private final ImmutableMap<String, Long> sizeByApkPaths;
  private final Variant variant;
  private final GetSizeRequest getSizeRequest;

  public VariantTotalSizeAggregator(
      ImmutableMap<String, Long> sizeByApkPaths, Variant variant, GetSizeRequest getSizeRequest) {
    this.sizeByApkPaths = sizeByApkPaths;
    this.variant = variant;
    this.getSizeRequest = getSizeRequest;
  }

  public ConfigurationSizes getSize() {
    if (isStandaloneApkVariant(variant)) {
      return getSizeStandaloneVariant();
    } else {
      return getSizeNonStandaloneVariant();
    }
  }

  private ConfigurationSizes getSizeNonStandaloneVariant() {
    ImmutableList<ApkDescription> apkDescriptions =
        variant.getApkSetList().stream()
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
            .collect(toImmutableList());

    ImmutableSet<AbiTargeting> abiTargetingOptions = getAllAbiTargetings(apkDescriptions);
    ImmutableSet<LanguageTargeting> languageTargetingOptions =
        getAllLanguageTargetings(apkDescriptions);
    ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions =
        getAllScreenDensityTargetings(apkDescriptions);

    return getSizesPerConfiguration(
        abiTargetingOptions, languageTargetingOptions, screenDensityTargetingOptions);
  }

  private ConfigurationSizes getSizeStandaloneVariant() {
    checkState(
        !getSizeRequest.getInstant(),
        "Standalone Variants cant be selected when instant flag is set");

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
                LanguageTargeting.getDefaultInstance()),
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

  private ImmutableSet<AbiTargeting> getAllAbiTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<AbiTargeting> abiTargetingOptions = ImmutableSet.builder();

    if (isAbiMissing(getSizeRequest.getDeviceSpec())) {
      abiTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasAbiTargeting)
              .map(ApkTargeting::getAbiTargeting)
              .collect(toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return abiTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(AbiTargeting.getDefaultInstance())
        : abiTargetingOptions.build();
  }

  private ImmutableSet<ScreenDensityTargeting> getAllScreenDensityTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<ScreenDensityTargeting> screenDensityTargetingOptions =
        ImmutableSet.builder();

    if (isScreenDensityMissing(getSizeRequest.getDeviceSpec())) {
      screenDensityTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasScreenDensityTargeting)
              .map(ApkTargeting::getScreenDensityTargeting)
              .collect(ImmutableSet.toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return screenDensityTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(ScreenDensityTargeting.getDefaultInstance())
        : screenDensityTargetingOptions.build();
  }

  private ImmutableSet<LanguageTargeting> getAllLanguageTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<LanguageTargeting> languageTargetingOptions = ImmutableSet.builder();

    if (isLocalesMissing(getSizeRequest.getDeviceSpec())) {
      languageTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasLanguageTargeting)
              .map(ApkTargeting::getLanguageTargeting)
              .collect(ImmutableSet.toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return languageTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(LanguageTargeting.getDefaultInstance())
        : languageTargetingOptions.build();
  }

  private ConfigurationSizes getSizesPerConfiguration(
      ImmutableSet<AbiTargeting> abiTargetingOptions,
      ImmutableSet<LanguageTargeting> languageTargetingOptions,
      ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions) {
    Map<SizeConfiguration, Long> minSizeByConfiguration = new HashMap<>();
    Map<SizeConfiguration, Long> maxSizeByConfiguration = new HashMap<>();

    SdkVersionTargeting sdkVersionTargeting = variant.getTargeting().getSdkVersionTargeting();
    for (AbiTargeting abiTargeting : abiTargetingOptions) {
      for (ScreenDensityTargeting screenDensityTargeting : screenDensityTargetingOptions) {
        for (LanguageTargeting languageTargeting : languageTargetingOptions) {

          SizeConfiguration configuration =
              mergeWithDeviceSpec(
                  getSizeConfiguration(
                      sdkVersionTargeting, abiTargeting, screenDensityTargeting, languageTargeting),
                  getSizeRequest.getDeviceSpec());

          long compressedSize =
              getCompressedSize(
                  new ApkMatcher(
                          getDeviceSpec(
                              getSizeRequest.getDeviceSpec(),
                              sdkVersionTargeting,
                              abiTargeting,
                              screenDensityTargeting,
                              languageTargeting),
                          getSizeRequest.getModules(),
                          getSizeRequest.getInstant())
                      .getMatchingApksFromVariant(variant));

          minSizeByConfiguration.merge(configuration, compressedSize, Math::min);
          maxSizeByConfiguration.merge(configuration, compressedSize, Math::max);
        }
      }
    }

    return ConfigurationSizes.create(
        /* minSizeConfigurationMap= */ ImmutableMap.copyOf(minSizeByConfiguration),
        /* maxSizeConfigurationMap= */ ImmutableMap.copyOf(maxSizeByConfiguration));
  }

  private SizeConfiguration getSizeConfiguration(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting) {

    ImmutableSet<Dimension> dimensions = getSizeRequest.getDimensions();
    SizeConfiguration.Builder sizeConfiguration = SizeConfiguration.builder();

    if (dimensions.contains(SDK)) {
      sizeConfiguration.setSdkVersion(SizeConfiguration.getSdkName(sdkVersionTargeting).orElse(""));
    }

    if (dimensions.contains(ABI)) {
      sizeConfiguration.setAbi(SizeConfiguration.getAbiName(abiTargeting).orElse(""));
    }

    if (dimensions.contains(SCREEN_DENSITY)) {
      sizeConfiguration.setScreenDensity(
          SizeConfiguration.getScreenDensityName(screenDensityTargeting).orElse(""));
    }

    if (dimensions.contains(LANGUAGE)) {
      sizeConfiguration.setLocale(SizeConfiguration.getLocaleName(languageTargeting).orElse(""));
    }

    return sizeConfiguration.build();
  }

  private DeviceSpec getDeviceSpec(
      DeviceSpec deviceSpec,
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting) {

    return new DeviceSpecFromTargetingBuilder(deviceSpec)
        .setSdkVersion(sdkVersionTargeting)
        .setSupportedAbis(abiTargeting)
        .setScreenDensity(screenDensityTargeting)
        .setSupportedLocales(languageTargeting)
        .build();
  }

  private SizeConfiguration mergeWithDeviceSpec(
      SizeConfiguration getSizeConfiguration, DeviceSpec deviceSpec) {

    ImmutableSet<Dimension> dimensions = getSizeRequest.getDimensions();
    SizeConfiguration.Builder mergedSizeConfiguration = getSizeConfiguration.toBuilder();
    if (dimensions.contains(ABI) && !isAbiMissing(deviceSpec)) {
      mergedSizeConfiguration.setAbi(COMMA_JOINER.join(deviceSpec.getSupportedAbisList()));
    }

    if (dimensions.contains(SCREEN_DENSITY) && !isScreenDensityMissing(deviceSpec)) {
      mergedSizeConfiguration.setScreenDensity(Integer.toString(deviceSpec.getScreenDensity()));
    }

    if (dimensions.contains(LANGUAGE) && !isLocalesMissing(deviceSpec)) {
      mergedSizeConfiguration.setLocale(COMMA_JOINER.join(deviceSpec.getSupportedLocalesList()));
    }

    if (dimensions.contains(SDK) && !isSdkVersionMissing(deviceSpec)) {
      mergedSizeConfiguration.setSdkVersion(String.format("%d", deviceSpec.getSdkVersion()));
    }

    return mergedSizeConfiguration.build();
  }

  /** Gets the total compressed sizes represented by the APK paths. */
  private long getCompressedSize(ImmutableList<ZipPath> apkPaths) {
    return apkPaths.stream().mapToLong(apkPath -> sizeByApkPaths.get(apkPath.toString())).sum();
  }
}
