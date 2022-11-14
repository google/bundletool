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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.TextureCompressionFormat;
import com.android.bundle.Targeting.TextureCompressionFormat.TextureCompressionFormatAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.utils.TargetingProtoUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.protobuf.Int32Value;
import java.util.Optional;

/** Utility functions for Targeting proto. */
public final class TargetingUtils {

  /** Returns the targeting dimensions of the targeting proto. */
  public static ImmutableList<TargetingDimension> getTargetingDimensions(
      AssetsDirectoryTargeting targeting) {
    ImmutableList.Builder<TargetingDimension> dimensions = new ImmutableList.Builder<>();
    if (targeting.hasAbi()) {
      dimensions.add(TargetingDimension.ABI);
    }
    if (targeting.hasTextureCompressionFormat()) {
      dimensions.add(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    }
    if (targeting.hasLanguage()) {
      dimensions.add(TargetingDimension.LANGUAGE);
    }
    if (targeting.hasDeviceTier()) {
      dimensions.add(TargetingDimension.DEVICE_TIER);
    }
    if (targeting.hasCountrySet()) {
      dimensions.add(TargetingDimension.COUNTRY_SET);
    }
    return dimensions.build();
  }

  /**
   * Given a set of potentially overlapping variant targetings generate smallest set of disjoint
   * variant targetings covering all of them.
   *
   * <p>Assumption: All Variants only support sdk targeting.
   */
  public static ImmutableSet<VariantTargeting> generateAllVariantTargetings(
      ImmutableSet<VariantTargeting> variantTargetings) {

    if (variantTargetings.size() <= 1) {
      return variantTargetings;
    }

    ImmutableList<SdkVersionTargeting> sdkVersionTargetings =
        disjointSdkTargetings(
            variantTargetings.stream()
                .map(variantTargeting -> variantTargeting.getSdkVersionTargeting())
                .collect(toImmutableList()));

    return sdkVersionTargetings.stream()
        .map(
            sdkVersionTargeting ->
                VariantTargeting.newBuilder().setSdkVersionTargeting(sdkVersionTargeting).build())
        .collect(toImmutableSet());
  }

  /**
   * Returns the filtered out or modified variant targetings that are an intersection of the given
   * variant targetings and SDK version range we want to support.
   *
   * <p>It is assumed that the variantTargetings contain only {@link SdkVersionTargeting}.
   *
   * <p>Each given and returned variant doesn't have the alternatives populated.
   */
  public static ImmutableSet<VariantTargeting> cropVariantsWithAppSdkRange(
      ImmutableSet<VariantTargeting> variantTargetings, Range<Integer> sdkRange) {
    ImmutableList<Range<Integer>> ranges = calculateVariantSdkRanges(variantTargetings, sdkRange);

    return ranges.stream()
        .map(range -> sdkVariantTargeting(range.lowerEndpoint()))
        .collect(toImmutableSet());
  }

  /**
   * Generates ranges of effective sdk versions each given variants covers intersected with the
   * range of SDK levels an app supports.
   *
   * <p>Variants that have no overlap with app's SDK level range are discarded.
   */
  private static ImmutableList<Range<Integer>> calculateVariantSdkRanges(
      ImmutableSet<VariantTargeting> variantTargetings, Range<Integer> appSdkRange) {
    return disjointSdkTargetings(
            variantTargetings.stream()
                .map(variantTargeting -> variantTargeting.getSdkVersionTargeting())
                .collect(toImmutableList()))
        .stream()
        .map(sdkTargeting -> Range.closedOpen(getMinSdk(sdkTargeting), getMaxSdk(sdkTargeting)))
        .filter(appSdkRange::isConnected)
        .map(appSdkRange::intersection)
        .filter(Predicates.not(Range::isEmpty))
        .collect(toImmutableList());
  }

  /**
   * Given a set of potentially overlapping sdk targetings generate set of disjoint sdk targetings
   * covering all of them.
   *
   * <p>Assumption: There are no sdk range gaps in targetings.
   */
  private static ImmutableList<SdkVersionTargeting> disjointSdkTargetings(
      ImmutableList<SdkVersionTargeting> sdkVersionTargetings) {

    sdkVersionTargetings.forEach(
        sdkVersionTargeting -> checkState(sdkVersionTargeting.getValueList().size() == 1));

    ImmutableList<Integer> minSdkValues =
        sdkVersionTargetings.stream()
            .map(sdkVersionTargeting -> sdkVersionTargeting.getValue(0).getMin().getValue())
            .distinct()
            .sorted()
            .collect(toImmutableList());

    ImmutableSet<SdkVersion> sdkVersions =
        minSdkValues.stream().map(TargetingProtoUtils::sdkVersionFrom).collect(toImmutableSet());

    return sdkVersions.stream()
        .map(
            sdkVersion ->
                sdkVersionTargeting(
                    sdkVersion,
                    Sets.difference(sdkVersions, ImmutableSet.of(sdkVersion)).immutableCopy()))
        .collect(toImmutableList());
  }

  /** Extracts the minimum sdk (inclusive) supported by the targeting. */
  public static int getMinSdk(SdkVersionTargeting sdkVersionTargeting) {
    if (sdkVersionTargeting.getValueList().isEmpty()) {
      return 1;
    }
    return Iterables.getOnlyElement(sdkVersionTargeting.getValueList()).getMin().getValue();
  }

  /** Extracts the maximum sdk (exclusive) supported by the targeting. */
  public static int getMaxSdk(SdkVersionTargeting sdkVersionTargeting) {
    int minSdk = getMinSdk(sdkVersionTargeting);
    int alternativeMinSdk =
        sdkVersionTargeting.getAlternativesList().stream()
            .mapToInt(alternativeSdk -> alternativeSdk.getMin().getValue())
            .filter(sdkValue -> minSdk < sdkValue)
            .min()
            .orElse(Integer.MAX_VALUE);

    return alternativeMinSdk;
  }

  private static VariantTargeting sdkVariantTargeting(int minSdk) {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder().addValue(TargetingProtoUtils.sdkVersionFrom(minSdk)))
        .build();
  }

  public static VariantTargeting standaloneApkVariantTargeting(ModuleSplit standaloneApk) {
    ApkTargeting apkTargeting = standaloneApk.getApkTargeting();

    VariantTargeting.Builder variantTargeting =
        sdkVariantTargeting(standaloneApk.getAndroidManifest().getEffectiveMinSdkVersion())
            .toBuilder();
    if (apkTargeting.hasAbiTargeting()) {
      variantTargeting.setAbiTargeting(apkTargeting.getAbiTargeting());
    }
    if (apkTargeting.hasScreenDensityTargeting()) {
      variantTargeting.setScreenDensityTargeting(apkTargeting.getScreenDensityTargeting());
    }
    if (apkTargeting.hasMultiAbiTargeting()) {
      variantTargeting.setMultiAbiTargeting(apkTargeting.getMultiAbiTargeting());
    }
    if (apkTargeting.hasTextureCompressionFormatTargeting()) {
      variantTargeting.setTextureCompressionFormatTargeting(
          apkTargeting.getTextureCompressionFormatTargeting());
    }

    return variantTargeting.build();
  }

  /**
   * Extracts a set of all directories in the module Assets.
   *
   * <p>This is only useful when BundleModule assets config is not yet generated (which is the case
   * when validators are run). Prefer using {@link BundleModule#getAssetsConfig} for all other
   * cases.
   */
  public static ImmutableSet<TargetedDirectory> extractAssetsTargetedDirectories(
      BundleModule module) {
    return module
        .findEntriesUnderPath(BundleModule.ASSETS_DIRECTORY)
        .map(ModuleEntry::getPath)
        .filter(path -> path.getNameCount() > 1)
        .map(ZipPath::getParent)
        .map(TargetedDirectory::parse)
        .collect(toImmutableSet());
  }

  /**
   * Extracts all the texture compression formats used in the targeted directories.
   *
   * <p>This is only useful when BundleModule assets config is not yet generated (which is the case
   * when validators are run). Prefer using {@link BundleModule#getAssetsConfig} for all other
   * cases.
   */
  public static ImmutableSet<TextureCompressionFormatAlias> extractTextureCompressionFormats(
      ImmutableSet<TargetedDirectory> targetedDirectories) {
    return targetedDirectories.stream()
        .map(directory -> directory.getTargeting(TargetingDimension.TEXTURE_COMPRESSION_FORMAT))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(targeting -> targeting.getTextureCompressionFormat().getValueList().stream())
        .map(TextureCompressionFormat::getAlias)
        .collect(toImmutableSet());
  }

  /**
   * Extracts all the device tiers used in the targeted directories.
   *
   * <p>This is only useful when BundleModule assets config is not yet generated (which is the case
   * when validators are run). Prefer using {@link BundleModule#getAssetsConfig} for all other
   * cases.
   */
  public static ImmutableSet<Integer> extractDeviceTiers(
      ImmutableSet<TargetedDirectory> targetedDirectories) {
    return targetedDirectories.stream()
        .map(TargetingUtils::extractDeviceTier)
        .flatMap(Streams::stream)
        .collect(toImmutableSet());
  }

  public static Optional<Integer> extractDeviceTier(TargetedDirectory targetedDirectory) {
    return targetedDirectory
        .getTargeting(TargetingDimension.DEVICE_TIER)
        .flatMap(
            targeting ->
                targeting.getDeviceTier().getValueList().stream()
                    .map(Int32Value::getValue)
                    .collect(toOptional()));
  }

  /**
   * Extracts all the country sets used in the targeted directories.
   *
   * <p>This is only useful when BundleModule assets config is not yet generated (which is the case
   * when validators are run). Prefer using {@link BundleModule#getAssetsConfig} for all other
   * cases.
   */
  public static ImmutableSet<String> extractCountrySets(
      ImmutableSet<TargetedDirectory> targetedDirectories) {
    return targetedDirectories.stream()
        .map(TargetingUtils::extractCountrySet)
        .flatMap(Streams::stream)
        .collect(toImmutableSet());
  }

  public static Optional<Assets> generateAssetsTargeting(BundleModule module) {
    ImmutableList<ZipPath> assetDirectories =
        module
            .findEntriesUnderPath(BundleModule.ASSETS_DIRECTORY)
            .map(ModuleEntry::getPath)
            .filter(path -> path.getNameCount() > 1)
            .map(ZipPath::getParent)
            .distinct()
            .collect(toImmutableList());

    if (assetDirectories.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForAssets(assetDirectories));
  }

  public static Optional<NativeLibraries> generateNativeLibrariesTargeting(BundleModule module) {
    // Validation ensures that files under "lib/" conform to pattern "lib/<abi-dir>/file.so".
    // We extract the distinct "lib/<abi-dir>" directories.
    ImmutableList<String> libAbiDirs =
        module
            .findEntriesUnderPath(BundleModule.LIB_DIRECTORY)
            .map(ModuleEntry::getPath)
            .filter(path -> path.getNameCount() > 2)
            .map(path -> path.subpath(0, 2))
            .map(ZipPath::toString)
            .distinct()
            .collect(toImmutableList());

    if (libAbiDirs.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new TargetingGenerator().generateTargetingForNativeLibraries(libAbiDirs));
  }

  public static Optional<ApexImages> generateApexImagesTargeting(BundleModule module) {
    // Validation ensures that image files under "apex/" conform to the pattern
    // "apex/<abi1>.<abi2>...<abiN>.img".
    ImmutableList<ZipPath> apexImageFiles =
        module
            .findEntriesUnderPath(BundleModule.APEX_DIRECTORY)
            .map(ModuleEntry::getPath)
            .filter(p -> p.toString().endsWith(BundleModule.APEX_IMAGE_SUFFIX))
            .collect(toImmutableList());

    // Validation ensures if build info is present then we have one per image.
    boolean hasBuildInfo =
        module
            .findEntriesUnderPath(BundleModule.APEX_DIRECTORY)
            .map(ModuleEntry::getPath)
            .anyMatch(p -> p.toString().endsWith(BundleModule.BUILD_INFO_SUFFIX));

    if (apexImageFiles.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new TargetingGenerator().generateTargetingForApexImages(apexImageFiles, hasBuildInfo));
  }

  private static Optional<String> extractCountrySet(TargetedDirectory targetedDirectory) {
    return targetedDirectory
        .getTargeting(TargetingDimension.COUNTRY_SET)
        .flatMap(
            targeting -> targeting.getCountrySet().getValueList().stream().collect(toOptional()));
  }
}
