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
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isLocalesMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isScreenDensityMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isSdkVersionMissing;
import static com.android.tools.build.bundletool.device.DeviceSpecUtils.isTextureCompressionFormatMissing;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.ABI;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.SDK;
import static com.android.tools.build.bundletool.model.GetSizeRequest.Dimension.TEXTURE_COMPRESSION_FORMAT;
import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.TARGETING_TO_TEXTURE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Common class to generate {@link ConfigurationSizes} for sets of APKs based on the requested
 * dimensions passed to {@link GetSizeCommand}.
 *
 * <p>Subclasses should implement the high level logic to get the total size ({@link getSize}) and
 * the logic to get the matching apks from a particular combination of targetings ({@link
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

  protected abstract ImmutableList<ZipPath> getMatchingApks(
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureTargeting);

  protected ImmutableSet<SdkVersionTargeting> getAllSdkVersionTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<SdkVersionTargeting> sdkVersionTargetingOptions = ImmutableSet.builder();

    if (isSdkVersionMissing(getSizeRequest.getDeviceSpec())) {
      sdkVersionTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasSdkVersionTargeting)
              .map(ApkTargeting::getSdkVersionTargeting)
              .collect(toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return sdkVersionTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(SdkVersionTargeting.getDefaultInstance())
        : sdkVersionTargetingOptions.build();
  }

  protected ImmutableSet<AbiTargeting> getAllAbiTargetings(
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

  protected ImmutableSet<ScreenDensityTargeting> getAllScreenDensityTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<ScreenDensityTargeting> screenDensityTargetingOptions =
        ImmutableSet.builder();

    if (isScreenDensityMissing(getSizeRequest.getDeviceSpec())) {
      screenDensityTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasScreenDensityTargeting)
              .map(ApkTargeting::getScreenDensityTargeting)
              .collect(toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return screenDensityTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(ScreenDensityTargeting.getDefaultInstance())
        : screenDensityTargetingOptions.build();
  }

  protected ImmutableSet<LanguageTargeting> getAllLanguageTargetings(
      ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet.Builder<LanguageTargeting> languageTargetingOptions = ImmutableSet.builder();

    if (isLocalesMissing(getSizeRequest.getDeviceSpec())) {
      languageTargetingOptions.addAll(
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasLanguageTargeting)
              .map(ApkTargeting::getLanguageTargeting)
              .collect(toImmutableSet()));
    }

    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return languageTargetingOptions.build().isEmpty()
        ? ImmutableSet.of(LanguageTargeting.getDefaultInstance())
        : languageTargetingOptions.build();
  }

  protected ImmutableSet<TextureCompressionFormatTargeting>
      getAllTextureCompressionFormatTargetings(ImmutableList<ApkDescription> apkDescriptions) {
    ImmutableSet<TextureCompressionFormatTargeting> textureCompressionFormatTargetingOptions;

    if (isTextureCompressionFormatMissing(getSizeRequest.getDeviceSpec())) {
      textureCompressionFormatTargetingOptions =
          apkDescriptions.stream()
              .map(ApkDescription::getTargeting)
              .filter(ApkTargeting::hasTextureCompressionFormatTargeting)
              .map(ApkTargeting::getTextureCompressionFormatTargeting)
              .collect(toImmutableSet());
    } else {
      textureCompressionFormatTargetingOptions = ImmutableSet.of();
    }
    // Adding default targeting (if targetings are empty) to help computing the cartesian product
    // across all targetings.
    return textureCompressionFormatTargetingOptions.isEmpty()
        ? ImmutableSet.of(TextureCompressionFormatTargeting.getDefaultInstance())
        : textureCompressionFormatTargetingOptions;
  }

  protected ConfigurationSizes getSizesPerConfiguration(
      ImmutableSet<SdkVersionTargeting> sdkTargetingOptions,
      ImmutableSet<AbiTargeting> abiTargetingOptions,
      ImmutableSet<LanguageTargeting> languageTargetingOptions,
      ImmutableSet<ScreenDensityTargeting> screenDensityTargetingOptions,
      ImmutableSet<TextureCompressionFormatTargeting> textureCompressionFormatTargetingOptions) {
    Map<SizeConfiguration, Long> minSizeByConfiguration = new HashMap<>();
    Map<SizeConfiguration, Long> maxSizeByConfiguration = new HashMap<>();

    for (SdkVersionTargeting sdkVersionTargeting : sdkTargetingOptions) {
      for (AbiTargeting abiTargeting : abiTargetingOptions) {
        for (ScreenDensityTargeting screenDensityTargeting : screenDensityTargetingOptions) {
          for (LanguageTargeting languageTargeting : languageTargetingOptions) {
            for (TextureCompressionFormatTargeting textureCompressionFormatTargeting :
                textureCompressionFormatTargetingOptions) {

              SizeConfiguration configuration =
                  mergeWithDeviceSpec(
                      getSizeConfiguration(
                          sdkVersionTargeting,
                          abiTargeting,
                          screenDensityTargeting,
                          languageTargeting,
                          textureCompressionFormatTargeting),
                      getSizeRequest.getDeviceSpec());

              long compressedSize =
                  getCompressedSize(
                      getMatchingApks(
                          sdkVersionTargeting,
                          abiTargeting,
                          screenDensityTargeting,
                          languageTargeting,
                          textureCompressionFormatTargeting));

              minSizeByConfiguration.merge(configuration, compressedSize, Math::min);
              maxSizeByConfiguration.merge(configuration, compressedSize, Math::max);
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
      TextureCompressionFormatTargeting textureCompressionFormatTargeting) {

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

    return sizeConfiguration.build();
  }

  protected DeviceSpec getDeviceSpec(
      DeviceSpec deviceSpec,
      SdkVersionTargeting sdkVersionTargeting,
      AbiTargeting abiTargeting,
      ScreenDensityTargeting screenDensityTargeting,
      LanguageTargeting languageTargeting,
      TextureCompressionFormatTargeting textureTargeting) {

    return new DeviceSpecFromTargetingBuilder(deviceSpec)
        .setSdkVersion(sdkVersionTargeting)
        .setSupportedAbis(abiTargeting)
        .setScreenDensity(screenDensityTargeting)
        .setSupportedLocales(languageTargeting)
        .setSupportedTextureCompressionFormats(textureTargeting)
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

    return mergedSizeConfiguration.build();
  }

  /** Gets the total compressed sizes represented by the APK paths. */
  private long getCompressedSize(ImmutableList<ZipPath> apkPaths) {
    return apkPaths.stream().mapToLong(apkPath -> sizeByApkPaths.get(apkPath.toString())).sum();
  }
}
