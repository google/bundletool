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

package com.android.tools.build.bundletool.testing;

import static com.android.tools.build.bundletool.model.utils.ProtoUtils.mergeFromProtos;
import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApexImageTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceFeature;
import com.android.bundle.Targeting.DeviceFeatureTargeting;
import com.android.bundle.Targeting.DeviceGroupModuleTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ModuleTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.bundle.Targeting.Sanitizer;
import com.android.bundle.Targeting.Sanitizer.SanitizerAlias;
import com.android.bundle.Targeting.SanitizerTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.bundle.Targeting.SdkRuntimeTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.TextureCompressionFormatTargeting;
import com.android.bundle.Targeting.UserCountriesTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Int32Value;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Utility functions for creating targeting configurations for tests. */
public final class TargetingUtils {

  // Assets.pb helper methods

  public static Assets assets(TargetedAssetsDirectory... directories) {
    return Assets.newBuilder().addAllDirectory(Lists.newArrayList(directories)).build();
  }

  public static TargetedAssetsDirectory targetedAssetsDirectory(
      String path, AssetsDirectoryTargeting targeting) {
    return TargetedAssetsDirectory.newBuilder().setPath(path).setTargeting(targeting).build();
  }

  public static AssetsDirectoryTargeting mergeAssetsTargeting(
      AssetsDirectoryTargeting targeting, AssetsDirectoryTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Assets directory targeting helpers for given targeting dimensions.
  // These should be written in terms of existing targeting proto helpers.
  // See below, for the targeting dimension helper methods.

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(AbiTargeting abiTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setAbi(abiTargeting).build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(String architecture) {
    AbiAlias alias = toAbiAlias(architecture);
    return assetsDirectoryTargeting(abiTargeting(alias));
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      TextureCompressionFormatTargeting textureCompressionFormatTargeting) {
    return AssetsDirectoryTargeting.newBuilder()
        .setTextureCompressionFormat(textureCompressionFormatTargeting)
        .build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      LanguageTargeting languageTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setLanguage(languageTargeting).build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      DeviceTierTargeting deviceTierTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setDeviceTier(deviceTierTargeting).build();
  }

  public static AssetsDirectoryTargeting assetsDirectoryTargeting(
      CountrySetTargeting countrySetTargeting) {
    return AssetsDirectoryTargeting.newBuilder().setCountrySet(countrySetTargeting).build();
  }

  // Native.pb helper methods.

  public static NativeLibraries nativeLibraries(TargetedNativeDirectory... nativeDirectories) {
    return NativeLibraries.newBuilder()
        .addAllDirectory(Lists.newArrayList(nativeDirectories))
        .build();
  }

  public static TargetedNativeDirectory targetedNativeDirectory(
      String path, NativeDirectoryTargeting targeting) {
    return TargetedNativeDirectory.newBuilder().setPath(path).setTargeting(targeting).build();
  }

  // Native directory targeting helpers for given targeting dimensions.
  // These should be written in terms of existing targeting proto helpers.
  // See below, for the targeting dimension helper methods.

  public static NativeDirectoryTargeting nativeDirectoryTargeting(AbiAlias abi) {
    return NativeDirectoryTargeting.newBuilder().setAbi(toAbi(abi)).build();
  }

  public static NativeDirectoryTargeting nativeDirectoryTargeting(String architecture) {
    AbiAlias alias = toAbiAlias(architecture);
    return nativeDirectoryTargeting(alias);
  }

  public static NativeDirectoryTargeting nativeDirectoryTargeting(
      AbiAlias abi, SanitizerAlias sanitizerAlias) {
    return NativeDirectoryTargeting.newBuilder()
        .setAbi(toAbi(abi))
        .setSanitizer(toSanitizer(sanitizerAlias))
        .build();
  }

  public static NativeDirectoryTargeting nativeDirectoryTargeting(
      TextureCompressionFormatAlias tcf) {
    return NativeDirectoryTargeting.newBuilder()
        .setTextureCompressionFormat(TextureCompressionFormat.newBuilder().setAlias(tcf))
        .build();
  }

  public static Sanitizer toSanitizer(SanitizerAlias sanitizerAlias) {
    return Sanitizer.newBuilder().setAlias(sanitizerAlias).build();
  }

  // Apex image file targeting helpers.

  /** Builds APEX images proto from a collection of targeted images. */
  public static ApexImages apexImages(TargetedApexImage... targetedApexImages) {
    return ApexImages.newBuilder().addAllImage(Lists.newArrayList(targetedApexImages)).build();
  }

  /** Builds APEX targeted image from the image file path and its targeting. */
  public static TargetedApexImage targetedApexImage(String path, ApexImageTargeting targeting) {
    return TargetedApexImage.newBuilder().setPath(path).setTargeting(targeting).build();
  }

  /** Builds APEX targeted image from the image file path and its multi-Abi targeting. */
  public static TargetedApexImage targetedApexImage(String path, MultiAbiTargeting targeting) {
    ApexImageTargeting apexImageTargeting =
        ApexImageTargeting.newBuilder().setMultiAbi(targeting).build();
    return TargetedApexImage.newBuilder().setPath(path).setTargeting(apexImageTargeting).build();
  }

  /** Builds APEX targeted image from the image file path, build info path and its targeting. */
  public static TargetedApexImage targetedApexImageWithBuildInfo(
      String path, String buildInfoPath, ApexImageTargeting targeting) {
    return TargetedApexImage.newBuilder()
        .setPath(path)
        .setBuildInfoPath(buildInfoPath)
        .setTargeting(targeting)
        .build();
  }

  /**
   * Builds APEX targeted image from the image file path, build info path and its multi-Abi
   * targeting.
   */
  public static TargetedApexImage targetedApexImageWithBuildInfo(
      String path, String buildInfoPath, MultiAbiTargeting targeting) {
    ApexImageTargeting apexImageTargeting =
        ApexImageTargeting.newBuilder().setMultiAbi(targeting).build();
    return TargetedApexImage.newBuilder()
        .setPath(path)
        .setBuildInfoPath(buildInfoPath)
        .setTargeting(apexImageTargeting)
        .build();
  }

  /** Builds APEX image targeting (no alternatives) according to the Abi names. */
  public static ApexImageTargeting apexImageTargeting(String... architectures) {
    MultiAbi.Builder multiAbi = MultiAbi.newBuilder();
    Arrays.stream(architectures).forEach(abi -> multiAbi.addAbi(toAbi(abi)));
    return ApexImageTargeting.newBuilder()
        .setMultiAbi(MultiAbiTargeting.newBuilder().addValue(multiAbi))
        .build();
  }

  // Apk Targeting helpers. Should be written in terms of existing targeting dimension protos or
  // helpers. See below, for the targeting dimension helper methods.

  public static ApkTargeting mergeApkTargeting(ApkTargeting targeting, ApkTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  public static ApkTargeting apkAbiTargeting(AbiTargeting abiTargeting) {
    return ApkTargeting.newBuilder().setAbiTargeting(abiTargeting).build();
  }

  public static ApkTargeting apkAbiTargeting(
      ImmutableSet<AbiAlias> abiAliases, ImmutableSet<AbiAlias> alternativeAbis) {
    return apkAbiTargeting(abiTargeting(abiAliases, alternativeAbis));
  }

  public static ApkTargeting apkAbiTargeting(
      AbiAlias abiAlias, ImmutableSet<AbiAlias> alternativeAbis) {
    return apkAbiTargeting(abiTargeting(abiAlias, alternativeAbis));
  }

  public static ApkTargeting apkAbiTargeting(AbiAlias abiAlias) {
    return apkAbiTargeting(abiTargeting(abiAlias));
  }

  public static ApkTargeting apkSanitizerTargeting(SanitizerAlias sanitizerAlias) {
    SanitizerTargeting sanitizerTargeting =
        SanitizerTargeting.newBuilder().addValue(toSanitizer(sanitizerAlias)).build();
    return ApkTargeting.newBuilder().setSanitizerTargeting(sanitizerTargeting).build();
  }

  /** Builds APK targeting, of multi-Abi targeting only. */
  public static ApkTargeting apkMultiAbiTargeting(MultiAbiTargeting multiAbiTargeting) {
    return ApkTargeting.newBuilder().setMultiAbiTargeting(multiAbiTargeting).build();
  }

  /** Builds APK targeting, of multi-Abi targeting of a single architecture. */
  public static ApkTargeting apkMultiAbiTargeting(AbiAlias abiAlias) {
    return apkMultiAbiTargeting(multiAbiTargeting(abiAlias));
  }

  /**
   * Builds APK targeting of a single architecture, with multi-Abi alternatives of single
   * architecture each.
   *
   * @param abiAlias single Abi to target by.
   * @param alternativeAbis a set of Abis, each one mapped to a single-Abi alternative (rather than
   *     one targeting of multiple Abis).
   */
  public static ApkTargeting apkMultiAbiTargeting(
      AbiAlias abiAlias, ImmutableSet<AbiAlias> alternativeAbis) {
    return apkMultiAbiTargeting(multiAbiTargeting(abiAlias, alternativeAbis));
  }

  /**
   * Builds APK multi-Abi targeting of arbitrary values and alternatives.
   *
   * @param abiAliases a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated MultiAbiTargeting.value.
   * @param alternatives a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated
   *     MultiAbiTargeting.alternatives.
   */
  public static ApkTargeting apkMultiAbiTargeting(
      ImmutableSet<ImmutableSet<AbiAlias>> abiAliases,
      ImmutableSet<ImmutableSet<AbiAlias>> alternatives) {
    return apkMultiAbiTargeting(multiAbiTargeting(abiAliases, alternatives));
  }

  /**
   * Builds APK multi-Abi targeting of single (multi Abi) values and arbitrary alternatives.
   *
   * @param targeting a set of Abi aliases, corresponding to a single value of MultiAbiTargeting.
   * @param allTargeting a set of all expected 'targeting' sets. The alternatives are built from
   *     this set minus the 'targeting' set.
   */
  public static ApkTargeting apkMultiAbiTargetingFromAllTargeting(
      ImmutableSet<AbiAlias> targeting, ImmutableSet<ImmutableSet<AbiAlias>> allTargeting) {
    return apkMultiAbiTargeting(
        ImmutableSet.of(targeting),
        Sets.difference(allTargeting, ImmutableSet.of(targeting)).immutableCopy());
  }

  public static ApkTargeting apkDensityTargeting(ScreenDensityTargeting screenDensityTargeting) {
    return ApkTargeting.newBuilder().setScreenDensityTargeting(screenDensityTargeting).build();
  }

  public static ApkTargeting apkDensityTargeting(
      ImmutableSet<DensityAlias> densities, Set<DensityAlias> alternativeDensities) {
    return apkDensityTargeting(screenDensityTargeting(densities, alternativeDensities));
  }

  public static ApkTargeting apkDensityTargeting(
      DensityAlias density, Set<DensityAlias> alternativeDensities) {
    return apkDensityTargeting(screenDensityTargeting(density, alternativeDensities));
  }

  public static ApkTargeting apkDensityTargeting(DensityAlias density) {
    return apkDensityTargeting(screenDensityTargeting(density));
  }

  public static ApkTargeting apkLanguageTargeting(LanguageTargeting languageTargeting) {
    return ApkTargeting.newBuilder().setLanguageTargeting(languageTargeting).build();
  }

  public static ApkTargeting apkLanguageTargeting(String... languages) {
    return apkLanguageTargeting(languageTargeting(languages));
  }

  public static ApkTargeting apkLanguageTargeting(
      ImmutableSet<String> languages, ImmutableSet<String> alternativeLanguages) {
    return apkLanguageTargeting(languageTargeting(languages, alternativeLanguages));
  }

  public static ApkTargeting apkAlternativeLanguageTargeting(String... alternativeLanguages) {
    return apkLanguageTargeting(alternativeLanguageTargeting(alternativeLanguages));
  }

  public static ApkTargeting apkMinSdkTargeting(int minSdkVersion) {
    return apkSdkTargeting(sdkVersionFrom(minSdkVersion));
  }

  public static ApkTargeting apkSdkTargeting(SdkVersion sdkVersion) {
    return ApkTargeting.newBuilder()
        .setSdkVersionTargeting(SdkVersionTargeting.newBuilder().addValue(sdkVersion))
        .build();
  }

  public static ApkTargeting apkTextureTargeting(
      TextureCompressionFormatAlias textureCompressionFormatAlias) {
    return apkTextureTargeting(textureCompressionTargeting(textureCompressionFormatAlias));
  }

  public static ApkTargeting apkTextureTargeting(
      TextureCompressionFormatAlias value,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return apkTextureTargeting(textureCompressionTargeting(value, alternatives));
  }

  public static ApkTargeting apkTextureTargeting(
      ImmutableSet<TextureCompressionFormatAlias> values,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return apkTextureTargeting(textureCompressionTargeting(values, alternatives));
  }

  public static ApkTargeting apkTextureTargeting(
      TextureCompressionFormatTargeting textureCompressionTargeting) {
    return ApkTargeting.newBuilder()
        .setTextureCompressionFormatTargeting(textureCompressionTargeting)
        .build();
  }

  public static ApkTargeting apkDeviceTierTargeting(DeviceTierTargeting deviceTierTargeting) {
    return ApkTargeting.newBuilder().setDeviceTierTargeting(deviceTierTargeting).build();
  }

  public static ApkTargeting apkCountrySetTargeting(CountrySetTargeting countrySetTargeting) {
    return ApkTargeting.newBuilder().setCountrySetTargeting(countrySetTargeting).build();
  }

  // Variant Targeting helpers. Should be written in terms of existing targeting dimension protos or
  // helpers. See below, for the targeting dimension helper methods.

  public static VariantTargeting variantAbiTargeting(AbiAlias value) {
    return variantAbiTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantAbiTargeting(
      AbiAlias value, ImmutableSet<AbiAlias> alternatives) {
    return VariantTargeting.newBuilder().setAbiTargeting(abiTargeting(value, alternatives)).build();
  }

  public static VariantTargeting variantAbiTargeting(Abi value) {
    return variantAbiTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantAbiTargeting(Abi value, ImmutableSet<Abi> alternatives) {
    return VariantTargeting.newBuilder()
        .setAbiTargeting(AbiTargeting.newBuilder().addValue(value).addAllAlternatives(alternatives))
        .build();
  }

  /** Builds variant targeting, of multi-Abi targeting only. */
  public static VariantTargeting variantMultiAbiTargeting(MultiAbiTargeting multiAbiTargeting) {
    return VariantTargeting.newBuilder().setMultiAbiTargeting(multiAbiTargeting).build();
  }

  /**
   * Builds variant multi-Abi targeting of a single architecture, with multi-Abi alternatives of
   * single architecture each.
   *
   * @param value single Abi to target by.
   * @param alternatives a set of Abis, each one mapped to a single-Abi alternative (rather than one
   *     targeting of multiple Abis).
   */
  public static VariantTargeting variantMultiAbiTargeting(
      AbiAlias value, ImmutableSet<AbiAlias> alternatives) {
    return variantMultiAbiTargeting(multiAbiTargeting(value, alternatives));
  }

  /**
   * Builds variant multi-Abi targeting of arbitrary values and alternatives.
   *
   * @param abiAliases a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated MultiAbiTargeting.value.
   * @param alternatives a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated
   *     MultiAbiTargeting.alternatives.
   */
  public static VariantTargeting variantMultiAbiTargeting(
      ImmutableSet<ImmutableSet<AbiAlias>> abiAliases,
      ImmutableSet<ImmutableSet<AbiAlias>> alternatives) {
    return variantMultiAbiTargeting(multiAbiTargeting(abiAliases, alternatives));
  }

  /**
   * Builds variant multi-Abi targeting of single (multi Abi) values and arbitrary alternatives.
   *
   * @param targeting a set of Abi aliases, corresponding to a single value of MultiAbiTargeting.
   * @param allTargeting a set of all expected 'targeting' sets. The alternatives are built from
   *     this set minus the 'targeting' set.
   */
  public static VariantTargeting variantMultiAbiTargetingFromAllTargeting(
      ImmutableSet<AbiAlias> targeting, ImmutableSet<ImmutableSet<AbiAlias>> allTargeting) {
    return variantMultiAbiTargeting(
        ImmutableSet.of(targeting),
        Sets.difference(allTargeting, ImmutableSet.of(targeting)).immutableCopy());
  }

  /** Builds Abi proto from its alias. */
  public static Abi toAbi(AbiAlias alias) {
    return Abi.newBuilder().setAlias(alias).build();
  }

  /** Builds Abi proto from the alias's name, given as a String. */
  public static Abi toAbi(String abi) {
    return toAbi(toAbiAlias(abi));
  }

  /** Builds AbiAlias proto from the alias's name, given as a String. */
  static AbiAlias toAbiAlias(String abi) {
    return AbiName.fromPlatformName(abi)
        .orElseThrow(() -> new IllegalArgumentException("Unrecognized ABI: " + abi))
        .toProto();
  }

  public static VariantTargeting variantMinSdkTargeting(
      int minSdkVersion, int... alternativeSdkVersions) {

    return variantSdkTargeting(
        sdkVersionFrom(minSdkVersion),
        Arrays.stream(alternativeSdkVersions)
            .mapToObj(TargetingUtils::sdkVersionFrom)
            .collect(toImmutableSet()));
  }

  public static VariantTargeting variantSdkTargeting(SdkVersion sdkVersion) {
    return variantSdkTargeting(sdkVersion, ImmutableSet.of());
  }

  public static VariantTargeting variantSdkTargeting(
      SdkVersion sdkVersion, ImmutableSet<SdkVersion> alternatives) {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(sdkVersion, alternatives))
        .build();
  }

  public static VariantTargeting variantSdkTargeting(int minSdkVersion) {
    return variantSdkTargeting(sdkVersionFrom(minSdkVersion), ImmutableSet.of());
  }

  public static VariantTargeting variantSdkTargeting(
      int minSdkVersion, ImmutableSet<Integer> alternativeMinSdkVersions) {
    return variantSdkTargeting(
        sdkVersionFrom(minSdkVersion),
        alternativeMinSdkVersions.stream()
            .map(TargetingUtils::sdkVersionFrom)
            .collect(toImmutableSet()));
  }

  public static VariantTargeting variantDensityTargeting(
      ScreenDensityTargeting screenDensityTargeting) {
    return VariantTargeting.newBuilder().setScreenDensityTargeting(screenDensityTargeting).build();
  }

  public static VariantTargeting variantDensityTargeting(DensityAlias value) {
    return variantDensityTargeting(screenDensityTargeting(value));
  }

  public static VariantTargeting variantDensityTargeting(
      ImmutableSet<DensityAlias> densities, ImmutableSet<DensityAlias> alternativeDensities) {
    return variantDensityTargeting(screenDensityTargeting(densities, alternativeDensities));
  }

  public static VariantTargeting variantDensityTargeting(
      DensityAlias density, ImmutableSet<DensityAlias> alternativeDensities) {
    return variantDensityTargeting(screenDensityTargeting(density, alternativeDensities));
  }

  public static VariantTargeting variantDensityTargeting(ScreenDensity value) {
    return variantDensityTargeting(value, ImmutableSet.of());
  }

  public static VariantTargeting variantDensityTargeting(
      ScreenDensity value, ImmutableSet<ScreenDensity> alternatives) {
    return VariantTargeting.newBuilder()
        .setScreenDensityTargeting(
            ScreenDensityTargeting.newBuilder().addValue(value).addAllAlternatives(alternatives))
        .build();
  }

  public static VariantTargeting variantTextureTargeting(TextureCompressionFormatAlias value) {
    return variantTextureTargeting(textureCompressionTargeting(value));
  }

  public static VariantTargeting variantTextureTargeting(
      ImmutableSet<TextureCompressionFormatAlias> values,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return variantTextureTargeting(textureCompressionTargeting(values, alternatives));
  }

  public static VariantTargeting variantTextureTargeting(
      TextureCompressionFormatAlias value,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return variantTextureTargeting(textureCompressionTargeting(value, alternatives));
  }

  public static VariantTargeting variantTextureTargeting(
      TextureCompressionFormatTargeting targeting) {
    return VariantTargeting.newBuilder().setTextureCompressionFormatTargeting(targeting).build();
  }

  public static VariantTargeting mergeVariantTargeting(
      VariantTargeting targeting, VariantTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Module Targeting helpers.

  public static ModuleTargeting moduleFeatureTargeting(String featureName) {
    return ModuleTargeting.newBuilder()
        .addDeviceFeatureTargeting(deviceFeatureTargeting(featureName))
        .build();
  }

  public static ModuleTargeting moduleFeatureTargeting(String featureName, int featureVersion) {
    return ModuleTargeting.newBuilder()
        .addDeviceFeatureTargeting(deviceFeatureTargeting(featureName, featureVersion))
        .build();
  }

  public static ModuleTargeting moduleMinSdkVersionTargeting(int minSdkVersion) {
    return ModuleTargeting.newBuilder()
        .setSdkVersionTargeting(sdkVersionTargeting(sdkVersionFrom(minSdkVersion)))
        .build();
  }

  /** Creates module targeting with provided max SDK version. */
  public static ModuleTargeting moduleMaxSdkVersionTargeting(int maxSdkVersion) {
    // Sentinel alternatives are not inclusive, hence +1.
    return ModuleTargeting.newBuilder()
        .setSdkVersionTargeting(maxOnlySdkVersionTargeting(sdkVersionFrom(maxSdkVersion + 1)))
        .build();
  }

  /** Creates module targeting with provided min and max SDK versions. */
  public static ModuleTargeting moduleMinMaxSdkVersionTargeting(
      int minSdkVersion, int maxSdkVersion) {
    // Sentinel alternatives are not inclusive, hence +1.
    return ModuleTargeting.newBuilder()
        .setSdkVersionTargeting(
            sdkVersionTargeting(sdkVersionFrom(minSdkVersion)).toBuilder()
                .addAlternatives(sdkVersionFrom(maxSdkVersion + 1))
                .build())
        .build();
  }

  public static ModuleTargeting moduleDeviceGroupsTargeting(String... deviceTiers) {
    return ModuleTargeting.newBuilder()
        .setDeviceGroupTargeting(
            DeviceGroupModuleTargeting.newBuilder().addAllValue(Arrays.asList(deviceTiers)))
        .build();
  }

  public static ModuleTargeting moduleExcludeCountriesTargeting(String... countries) {
    return moduleCountriesTargeting(true, countries);
  }

  public static ModuleTargeting moduleIncludeCountriesTargeting(String... countries) {
    return moduleCountriesTargeting(false, countries);
  }

  public static ModuleTargeting moduleCountriesTargeting(boolean exclude, String... countries) {
    return ModuleTargeting.newBuilder()
        .setUserCountriesTargeting(
            UserCountriesTargeting.newBuilder()
                .addAllCountryCodes(Arrays.asList(countries))
                .setExclude(exclude))
        .build();
  }

  public static ModuleTargeting mergeModuleTargeting(
      ModuleTargeting targeting, ModuleTargeting... targetings) {
    return mergeFromProtos(targeting, targetings);
  }

  // Per-dimension targeting helper methods.

  // ABI targeting.

  public static AbiTargeting abiTargeting(AbiAlias abi) {
    return abiTargeting(ImmutableSet.of(abi), ImmutableSet.of());
  }

  public static AbiTargeting abiTargeting(AbiAlias abi, ImmutableSet<AbiAlias> alternatives) {
    return abiTargeting(ImmutableSet.of(abi), alternatives);
  }

  public static AbiTargeting abiTargeting(
      ImmutableSet<AbiAlias> abiAliases, ImmutableSet<AbiAlias> alternatives) {
    return AbiTargeting.newBuilder()
        .addAllValue(abiAliases.stream().map(TargetingUtils::toAbi).collect(toImmutableList()))
        .addAllAlternatives(
            alternatives.stream().map(TargetingUtils::toAbi).collect(toImmutableList()))
        .build();
  }

  // Multi ABI targeting

  /** Builds multi-Abi targeting of a single value, of one architecture (no alternatives). */
  public static MultiAbiTargeting multiAbiTargeting(AbiAlias abi) {
    return multiAbiTargeting(ImmutableSet.of(ImmutableSet.of(abi)), ImmutableSet.of());
  }

  /**
   * Builds multi-Abi targeting of arbitrary values with no alternatives.
   *
   * @param abiAliases a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated MultiAbiTargeting.value.
   */
  public static MultiAbiTargeting multiAbiTargeting(
      ImmutableSet<ImmutableSet<AbiAlias>> abiAliases) {
    return MultiAbiTargeting.newBuilder().addAllValue(buildMultiAbis(abiAliases)).build();
  }

  /**
   * Builds multi-Abi targeting of a single architecture, with multi-Abi alternatives of single
   * architecture each.
   *
   * @param abi single Abi to target by.
   * @param alternatives a set of Abis, each one mapped to a single-Abi alternative (rather than one
   *     targeting of multiple Abis).
   */
  public static MultiAbiTargeting multiAbiTargeting(
      AbiAlias abi, ImmutableSet<AbiAlias> alternatives) {
    // Each element in 'alternatives' represent an alternative, not a MultiAbi.
    ImmutableSet<ImmutableSet<AbiAlias>> alternativeSet =
        alternatives.stream().map(ImmutableSet::of).collect(toImmutableSet());
    return multiAbiTargeting(ImmutableSet.of(ImmutableSet.of(abi)), alternativeSet);
  }

  /**
   * Builds multi-Abi targeting of arbitrary values and alternatives.
   *
   * @param abiAliases a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated MultiAbiTargeting.value.
   * @param alternatives a set of sets of Abi aliases. Each inner set is converted to the repeated
   *     MultiAbi.abi, and the outer set is converted to the repeated
   *     MultiAbiTargeting.alternatives.
   */
  public static MultiAbiTargeting multiAbiTargeting(
      ImmutableSet<ImmutableSet<AbiAlias>> abiAliases,
      ImmutableSet<ImmutableSet<AbiAlias>> alternatives) {
    return MultiAbiTargeting.newBuilder()
        .addAllValue(buildMultiAbis(abiAliases))
        .addAllAlternatives(buildMultiAbis(alternatives))
        .build();
  }

  private static ImmutableList<MultiAbi> buildMultiAbis(
      ImmutableSet<ImmutableSet<AbiAlias>> abiAliases) {
    return abiAliases.stream()
        .map(
            aliases ->
                MultiAbi.newBuilder()
                    .addAllAbi(
                        aliases.stream().map(TargetingUtils::toAbi).collect(toImmutableList()))
                    .build())
        .collect(toImmutableList());
  }

  // Screen Density targeting.

  public static ScreenDensityTargeting screenDensityTargeting(
      ImmutableSet<DensityAlias> densities, Set<DensityAlias> alternativeDensities) {
    return ScreenDensityTargeting.newBuilder()
        .addAllValue(
            densities.stream().map(TargetingUtils::toScreenDensity).collect(toImmutableList()))
        .addAllAlternatives(
            alternativeDensities.stream()
                .map(TargetingUtils::toScreenDensity)
                .collect(toImmutableList()))
        .build();
  }

  public static ScreenDensityTargeting screenDensityTargeting(
      int densityDpiValue, ImmutableSet<DensityAlias> alternativeDensities) {
    return ScreenDensityTargeting.newBuilder()
        .addValue(toScreenDensity(densityDpiValue))
        .addAllAlternatives(
            alternativeDensities.stream()
                .map(TargetingUtils::toScreenDensity)
                .collect(toImmutableList()))
        .build();
  }

  public static ScreenDensityTargeting screenDensityTargeting(
      DensityAlias density, Set<DensityAlias> alternativeDensities) {
    return screenDensityTargeting(ImmutableSet.of(density), alternativeDensities);
  }

  public static ScreenDensityTargeting screenDensityTargeting(DensityAlias density) {
    return screenDensityTargeting(ImmutableSet.of(density), ImmutableSet.of());
  }

  public static ScreenDensity toScreenDensity(DensityAlias densityAlias) {
    return ScreenDensity.newBuilder().setDensityAlias(densityAlias).build();
  }

  public static ScreenDensity toScreenDensity(int densityDpi) {
    return ScreenDensity.newBuilder().setDensityDpi(densityDpi).build();
  }

  // Language targeting.

  /**
   * Deliberately private, because bundletool should never produce language targeting with both
   * `values` and `alternatives`.
   */
  private static LanguageTargeting languageTargeting(
      ImmutableSet<String> languages, ImmutableSet<String> alternativeLanguages) {
    return LanguageTargeting.newBuilder()
        .addAllValue(languages)
        .addAllAlternatives(alternativeLanguages)
        .build();
  }

  /**
   * This method should be used only in highly-specialized tests.
   *
   * @deprecated Bundletool never produces language targeting with both `values` and `alternatives`
   *     set.
   */
  @Deprecated
  public static LanguageTargeting languageTargeting(
      String language, ImmutableSet<String> alternativeLanguages) {
    return languageTargeting(ImmutableSet.of(language), alternativeLanguages);
  }

  public static LanguageTargeting languageTargeting(String... languages) {
    return languageTargeting(ImmutableSet.copyOf(languages), ImmutableSet.of());
  }

  public static LanguageTargeting alternativeLanguageTargeting(String... alternativeLanguages) {
    return languageTargeting(ImmutableSet.of(), ImmutableSet.copyOf(alternativeLanguages));
  }

  // SDK Version targeting.

  public static SdkVersion sdkVersionFrom(int from) {
    return SdkVersion.newBuilder().setMin(Int32Value.of(from)).build();
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

  public static SdkVersionTargeting maxOnlySdkVersionTargeting(SdkVersion sdkVersion) {
    return sdkVersionTargeting(sdkVersionFrom(1), /* alternatives= */ ImmutableSet.of(sdkVersion));
  }

  // Texture Compression Format targeting.

  public static TextureCompressionFormat textureCompressionFormat(
      TextureCompressionFormatAlias alias) {
    return TextureCompressionFormat.newBuilder().setAlias(alias).build();
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      ImmutableSet<TextureCompressionFormatAlias> values,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return TextureCompressionFormatTargeting.newBuilder()
        .addAllValue(
            values.stream()
                .map(alias -> TextureCompressionFormat.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .addAllAlternatives(
            alternatives.stream()
                .map(alias -> TextureCompressionFormat.newBuilder().setAlias(alias).build())
                .collect(toImmutableList()))
        .build();
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      TextureCompressionFormatAlias value) {
    return textureCompressionTargeting(ImmutableSet.of(value), ImmutableSet.of());
  }

  public static TextureCompressionFormatTargeting textureCompressionTargeting(
      TextureCompressionFormatAlias value,
      ImmutableSet<TextureCompressionFormatAlias> alternatives) {
    return textureCompressionTargeting(ImmutableSet.of(value), alternatives);
  }

  public static TextureCompressionFormatTargeting alternativeTextureCompressionTargeting(
      TextureCompressionFormatAlias... alternatives) {
    return textureCompressionTargeting(ImmutableSet.of(), ImmutableSet.copyOf(alternatives));
  }

  // Device Tier targeting.

  public static DeviceTierTargeting deviceTierTargeting(int value) {
    return DeviceTierTargeting.newBuilder().addValue(Int32Value.of(value)).build();
  }

  public static DeviceTierTargeting deviceTierTargeting(
      int value, ImmutableList<Integer> alternatives) {
    return DeviceTierTargeting.newBuilder()
        .addValue(Int32Value.of(value))
        .addAllAlternatives(alternatives.stream().map(Int32Value::of).collect(toImmutableList()))
        .build();
  }

  public static DeviceTierTargeting alternativeDeviceTierTargeting(
      ImmutableList<Integer> alternatives) {
    return DeviceTierTargeting.newBuilder()
        .addAllAlternatives(alternatives.stream().map(Int32Value::of).collect(toImmutableList()))
        .build();
  }

  // Country Set targeting.

  public static CountrySetTargeting countrySetTargeting(String value) {
    return CountrySetTargeting.newBuilder().addValue(value).build();
  }

  public static CountrySetTargeting countrySetTargeting(
      String value, ImmutableList<String> alternatives) {
    return CountrySetTargeting.newBuilder()
        .addValue(value)
        .addAllAlternatives(alternatives)
        .build();
  }

  public static CountrySetTargeting countrySetTargeting(
      ImmutableList<String> value, ImmutableList<String> alternatives) {
    return CountrySetTargeting.newBuilder()
        .addAllValue(value)
        .addAllAlternatives(alternatives)
        .build();
  }

  public static CountrySetTargeting alternativeCountrySetTargeting(
      ImmutableList<String> alternatives) {
    return CountrySetTargeting.newBuilder().addAllAlternatives(alternatives).build();
  }

  // Device Feature targeting.

  public static DeviceFeatureTargeting deviceFeatureTargeting(String featureName) {
    return DeviceFeatureTargeting.newBuilder()
        .setRequiredFeature(DeviceFeature.newBuilder().setFeatureName(featureName))
        .build();
  }

  public static DeviceFeatureTargeting deviceFeatureTargeting(
      String featureName, int featureVersion) {
    return DeviceFeatureTargeting.newBuilder()
        .setRequiredFeature(
            DeviceFeature.newBuilder()
                .setFeatureName(featureName)
                .setFeatureVersion(featureVersion))
        .build();
  }

  public static ImmutableList<DeviceFeatureTargeting> deviceFeatureTargetingList(
      String... featureNames) {
    return Arrays.asList(featureNames).stream()
        .map(TargetingUtils::deviceFeatureTargeting)
        .collect(toImmutableList());
  }

  // Helper methods for processing splits.

  public static ImmutableList<ModuleSplit> filterSplitsByTargeting(
      Collection<ModuleSplit> splits,
      Predicate<ApkTargeting> apkTargetingPredicate,
      Predicate<VariantTargeting> variantTargetingPredicate) {
    return splits.stream()
        .filter(moduleSplit -> apkTargetingPredicate.test(moduleSplit.getApkTargeting()))
        .filter(moduleSplit -> variantTargetingPredicate.test(moduleSplit.getVariantTargeting()))
        .collect(toImmutableList());
  }

  public static ImmutableList<ModuleSplit> getSplitsWithTargetingEqualTo(
      Collection<ModuleSplit> splits, ApkTargeting apkTargeting) {
    return filterSplitsByTargeting(
        splits, apkTargeting::equals, /* variantTargetingPredicate= */ alwaysTrue());
  }

  public static ImmutableList<ModuleSplit> getSplitsWithDefaultTargeting(
      Collection<ModuleSplit> splits) {
    return getSplitsWithTargetingEqualTo(splits, ApkTargeting.getDefaultInstance());
  }

  public static void assertForSingleDefaultSplit(
      Collection<ModuleSplit> splits, Consumer<ModuleSplit> assertionFn) {
    ImmutableList<ModuleSplit> defaultSplits = getSplitsWithDefaultTargeting(splits);
    assertThat(defaultSplits).hasSize(1);
    assertionFn.accept(defaultSplits.get(0));
  }

  public static void assertForNonDefaultSplits(
      Collection<ModuleSplit> splits, Consumer<ModuleSplit> assertionFn) {
    ImmutableList<ModuleSplit> nonDefaultSplits =
        filterSplitsByTargeting(
            splits, Predicates.not(ApkTargeting.getDefaultInstance()::equals), alwaysTrue());
    assertThat(nonDefaultSplits).isNotEmpty();
    nonDefaultSplits.stream().forEach(assertionFn);
  }

  public static VariantTargeting lPlusVariantTargeting() {
    return variantMinSdkTargeting(Versions.ANDROID_L_API_VERSION);
  }

  public static VariantTargeting sdkRuntimeVariantTargeting() {
    return sdkRuntimeVariantTargeting(Versions.ANDROID_T_API_VERSION);
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(SdkVersion androidSdkVersion) {
    return sdkRuntimeVariantTargeting(androidSdkVersion.getMin().getValue());
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(int androidSdkVersion) {
    return sdkRuntimeVariantTargeting(
        androidSdkVersion, /* alternativeSdkVersions= */ ImmutableSet.of());
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(
      SdkVersion androidSdkVersion, ImmutableSet<SdkVersion> alternativeSdkVersions) {
    return sdkRuntimeVariantTargeting(
        androidSdkVersion.getMin().getValue(),
        alternativeSdkVersions.stream()
            .map(sdkVersion -> sdkVersion.getMin().getValue())
            .collect(toImmutableSet()));
  }

  public static VariantTargeting sdkRuntimeVariantTargeting(
      int androidSdkVersion, ImmutableSet<Integer> alternativeSdkVersions) {
    return VariantTargeting.newBuilder()
        .setSdkRuntimeTargeting(SdkRuntimeTargeting.newBuilder().setRequiresSdkRuntime(true))
        .setSdkVersionTargeting(
            sdkVersionTargeting(
                sdkVersionFrom(androidSdkVersion),
                alternativeSdkVersions.stream()
                    .map(TargetingUtils::sdkVersionFrom)
                    .collect(toImmutableSet())))
        .build();
  }

  // Not meant to be instantiated.
  private TargetingUtils() {}
}
