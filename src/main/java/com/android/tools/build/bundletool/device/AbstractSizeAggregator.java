/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.device.DeviceSpecUtils.getDeviceSupportedTextureCompressionFormats;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isAbiMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isDeviceTierMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isLocalesMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isScreenDensityMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isSdkRuntimeUnspecified;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isSdkVersionMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isTextureCompressionFormatMissing;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ABI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.COUNTRY_SET;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.DEVICE_TIER;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK_RUNTIME;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.TARGETING_TO_TEXTURE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.tools.build.bundletool.commands.GetSizeCommand;
import com.android.tools.build.bundletool.device.ApkMatcher.GeneratedApk;
import com.android.tools.build.bundletool.device.DeviceSpecUtils.DeviceSpecFromTargetingBuilder;
import com.android.tools.build.bundletool.model.ConfigurationSizes;
import com.android.tools.build.bundletool.model.GetSizeRequest;
import com.android.tools.build.bundletool.model.GetSizeRequest.Dimension;
import com.android.tools.build.bundletool.model.SizeConfiguration;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Common class to generate {@link ConfigurationSizes} for sets of APKs based on the requested
 * dimensions passed to {@link GetSizeCommand}.
 *
 * <p>Subclasses should implement the high level logic to get the total size ({@code getSize}) and
 * the logic to get the matching apks from a particular combination of targetings ({@code
 * getMatchingApks}).
 */
public abstract class AbstractSizeAggregator {

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  protected final ImmutableMap<String, Long> sizeByApkPaths;
  protected final GetSizeRequest getSizeRequest;

  protected AbstractSizeAggregator(
      ImmutableMap<String, Long> sizeByApkPaths, GetSizeRequest getSizeRequest) {
    this.sizeByApkPaths = sizeByApkPaths;
    this.getSizeRequest = getSizeRequest;
  }

  /** Aggregate the sizes of a set of APKs info a {@link ConfigurationSizes}. */
  public abstract ConfigurationSizes getSize();

  protected abstract ImmutableList<GeneratedApk> getMatchingApks(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureTargeting,
      DeviceTierTargeting deviceTierTargeting,
      CountrySetTargeting countrySetTargeting,
      SdkRuntimeTargeting sdkRuntimeTargeting);

  protected ImmutableSet<SdkVersionTargeting> getAllSdkVersionTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isSdkVersionMissing,
        ApkTargeting::hasSdkVersionTargeting,
        ApkTargeting::getSdkVersionTargeting,
        SdkVersionTargeting.getDefaultInstance());
  }

  protected ImmutableSet<AbiTargeting> getAllAbiTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isAbiMissing,
        ApkTargeting::hasAbiTargeting,
        ApkTargeting::getAbiTargeting,
        AbiTargeting.getDefaultInstance());
  }

  protected ImmutableSet<ScreenDensityTargeting> getAllScreenDensityTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isScreenDensityMissing,
        ApkTargeting::hasScreenDensityTargeting,
        ApkTargeting::getScreenDensityTargeting,
        ScreenDensityTargeting.getDefaultInstance());
  }

  protected ImmutableSet<LanguageTargeting> getAllLanguageTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isLocalesMissing,
        ApkTargeting::hasLanguageTargeting,
        ApkTargeting::getLanguageTargeting,
        LanguageTargeting.getDefaultInstance());
  }

  protected ImmutableSet<TextureCompressionFormatTargeting>
      getAllTextureCompressionFormatTargetings(ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isTextureCompressionFormatMissing,
        ApkTargeting::hasTextureCompressionFormatTargeting,
        ApkTargeting::getTextureCompressionFormatTargeting,
        TextureCompressionFormatTargeting.getDefaultInstance());
  }

  protected ImmutableSet<DeviceTierTargeting> getAllDeviceTierTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isDeviceTierMissing,
        ApkTargeting::hasDeviceTierTargeting,
        ApkTargeting::getDeviceTierTargeting,
        DeviceTierTargeting.getDefaultInstance());
  }

  protected ImmutableSet<CountrySetTargeting> getAllCountrySetTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    return getAllTargetings(
        apkDescriptions,
        DeviceSpecUtils::isCountrySetMissing,
        ApkTargeting::hasCountrySetTargeting,
        ApkTargeting::getCountrySetTargeting,
        CountrySetTargeting.getDefaultInstance());
  }

  /** Retrieves all targetings for a generic dimension. */
  protected <T extends Message> ImmutableSet<T> getAllTargetings(
      ImmutableList<ApkDescription> apkDescriptions,
      Predicate<DeviceSpec> isDimensionMissingFromDevice,
      Predicate<ApkTargeting> hasTargeting,
      Function<ApkTargeting, T> getTargeting,
      T defaultInstance) {
    ImmutableSet<T> targetingOptions;

    if (isDimensionMissingFromDevice.test(getSizeRequest.getDeviceSpec())) {
      targetingOptions =
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(hasTargeting)
              .map(getTargeting)
              .collect(toImmutableSet());
    } else {
      targetingOptions = ImmutableSet.of();
    }
    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return targetingOptions.isEmpty() ? ImmutableSet.of(defaultInstance) : targetingOptions;
  }

  protected ConfigurationSizes getSizesPerConfiguration(
      ImmutableSet<SdkVersionTargeting> sdkTargetingOptions,
      ImmutableSet<AbiTargeting> abiTargetingOptions,
      ImmutableSet<LanguageTargeting> languageTargetingOptions,
      ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions,
      ImmutableSet<TextureCompressionFormatTargeting> textureCompressionFormatTargetingOptions,
      ImmutableSet<DeviceTierTargeting> deviceTierTargetingOptions,
      ImmutableSet<CountrySetTargeting> countrySetTargetingOptions,
      // We use a single value instead of a set for SdkRuntimeTargeting since one variant can only
      // have one value for this dimension.
      SdkRuntimeTargeting sdkRuntimeTargeting) {
    Map<SizeConfiguration, Long> minSizeByConfiguration = new HashMap<>();
    Map<SizeConfiguration, Long> maxSizeByConfiguration = new HashMap<>();

    for (SdkVersionTargeting sdkVersionTargeting : sdkTargetingOptions) {
      for (AbiTargeting abiTargeting : abiTargetingOptions) {
        for (ScreenDensityTargeting screenDensityTargeting : screenDensityTargetingOptions) {
          for (LanguageTargeting languageTargeting : languageTargetingOptions) {
            for (TextureCompressionFormatTargeting textureCompressionFormatTargeting :
                textureCompressionFormatTargetingOptions) {
              for (DeviceTierTargeting deviceTierTargeting : deviceTierTargetingOptions) {
                for (CountrySetTargeting countrySetTargeting : countrySetTargetingOptions) {

                  SizeConfiguration configuration =
                      mergeWithDeviceSpec(
                          getSizeConfiguration(
                              sdkVersionTargeting,
                              abiTargeting,
                              screenDensityTargeting,
                              languageTargeting,
                              textureCompressionFormatTargeting,
                              deviceTierTargeting,
                              countrySetTargeting,
                              sdkRuntimeTargeting),
                          getSizeRequest.getDeviceSpec());

                  long compressedSize =
                      getCompressedSize(
                          getMatchingApks(
                              sdkVersionTargeting,
                              abiTargeting,
                              screenDensityTargeting,
                              languageTargeting,
                              textureCompressionFormatTargeting,
                              deviceTierTargeting,
                              countrySetTargeting,
                              sdkRuntimeTargeting));

                  minSizeByConfiguration.merge(configuration, compressedSize, Math::min);
                  maxSizeByConfiguration.merge(configuration, compressedSize, Math::max);
                }
              }
            }
          }
        }
      }
    }

    return ConfigurationSizes.create(
        /* minSizeConfigurationMap= */ ImmutableMap.copyOf(minSizeByConfiguration),
        /* maxSizeConfigurationMap= */ ImmutableMap.copyOf(maxSizeByConfiguration));
  }

  protected SizeConfiguration getSizeConfiguration(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureCompressionFormatTargeting,
      DeviceTierTargeting deviceTierTargeting,
      CountrySetTargeting countrySetTargeting,
      SdkRuntimeTargeting sdkRuntimeTargeting) {

    ImmutableSet<Dimension> dimensions = getSizeRequest.getDimensions();
    SizeConfiguration.Builder sizeConfiguration = SizeConfiguration.builder();

    if (dimensions.contains(SDK)) {
      SizeConfiguration.getSdkName(sdkVersionTargeting).ifPresent(sizeConfiguration::setSdkVersion);
    }

    if (dimensions.contains(ABI)) {
      SizeConfiguration.getAbiName(abiTargeting).ifPresent(sizeConfiguration::setAbi);
    }

    if (dimensions.contains(SCREEN_DENSITY)) {
      SizeConfiguration.getScreenDensityName(screenDensityTargeting)
          .ifPresent(sizeConfiguration::setScreenDensity);
    }

    if (dimensions.contains(LANGUAGE)) {
      SizeConfiguration.getLocaleName(languageTargeting).ifPresent(sizeConfiguration::setLocale);
    }

    if (dimensions.contains(TEXTURE_COMPRESSION_FORMAT)) {
      SizeConfiguration.getTextureCompressionFormatName(textureCompressionFormatTargeting)
          .ifPresent(sizeConfiguration::setTextureCompressionFormat);
    }

    if (dimensions.contains(DEVICE_TIER)) {
      SizeConfiguration.getDeviceTierLevel(deviceTierTargeting)
          .ifPresent(sizeConfiguration::setDeviceTier);
    }

    if (dimensions.contains(COUNTRY_SET)) {
      SizeConfiguration.getCountrySetName(countrySetTargeting)
          .ifPresent(sizeConfiguration::setCountrySet);
    }

    if (dimensions.contains(SDK_RUNTIME)) {
      sizeConfiguration.setSdkRuntime(SizeConfiguration.getSdkRuntimeRequired(sdkRuntimeTargeting));
    }

    return sizeConfiguration.build();
  }

  protected DeviceSpec getDeviceSpec(
      DeviceSpec deviceSpec,
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureTargeting,
      DeviceTierTargeting deviceTierTargeting,
      CountrySetTargeting countrySetTargeting,
      SdkRuntimeTargeting sdkRuntimeTargeting) {

    return new DeviceSpecFromTargetingBuilder(deviceSpec)
        .setSdkVersion(sdkVersionTargeting)
        .setSupportedAbis(abiTargeting)
        .setScreenDensity(screenDensityTargeting)
        .setSupportedLocales(languageTargeting)
        .setSupportedTextureCompressionFormats(textureTargeting)
        .setDeviceTier(deviceTierTargeting)
        .setCountrySet(countrySetTargeting)
        .setSdkRuntime(sdkRuntimeTargeting)
        .build();
  }

  protected SizeConfiguration mergeWithDeviceSpec(
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

    if (dimensions.contains(TEXTURE_COMPRESSION_FORMAT)
        && !isTextureCompressionFormatMissing(deviceSpec)) {
      mergedSizeConfiguration.setTextureCompressionFormat(
          COMMA_JOINER.join(
              getDeviceSupportedTextureCompressionFormats(deviceSpec).stream()
                  .map(TARGETING_TO_TEXTURE::get)
                  .collect(toImmutableList())));
    }

    if (dimensions.contains(DEVICE_TIER) && !isDeviceTierMissing(deviceSpec)) {
      mergedSizeConfiguration.setDeviceTier(deviceSpec.getDeviceTier().getValue());
    }

    if (dimensions.contains(SDK_RUNTIME) && !isSdkRuntimeUnspecified(deviceSpec)) {
      mergedSizeConfiguration.setSdkRuntime(
          deviceSpec.getSdkRuntime().getSupported() ? "Required" : "Not Required");
    }

    return mergedSizeConfiguration.build();
  }

  /** Gets the total compressed sizes represented by the APK paths. */
  private long getCompressedSize(ImmutableList<GeneratedApk> apks) {
    return apks.stream().mapToLong(apk -> sizeByApkPaths.get(apk.getPath().toString())).sum();
  }
}
