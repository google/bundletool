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
import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.TEXTURE_TO_TARGETING;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
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
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
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
    if (targeting.hasGraphicsApi()) {
      dimensions.add(TargetingDimension.GRAPHICS_API);
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
   * Update the module to have the targeting specified by a default value for a dimension. This
   * means that the Apk and Variant targeting for this module will have the value passed for this
   * dimension - or none if the value is empty.
   */
  public static ModuleSplit setTargetingByDefaultSuffix(
      ModuleSplit moduleSplit, TargetingDimension dimension, String value) {
    // Only TCF is supported for now in targeting by a default suffix.
    checkArgument(dimension.equals(TargetingDimension.TEXTURE_COMPRESSION_FORMAT));

    // If the value is empty, we don't need to modify the targeting of the module split.
    if (value.isEmpty()) {
      return moduleSplit;
    }

    // Apply the updated targeting to the module split (as it now only contains assets for
    // the selected TCF), both for the APK and the variant targeting.
    return moduleSplit.toBuilder()
        .setApkTargeting(
            moduleSplit.getApkTargeting().toBuilder()
                .setTextureCompressionFormatTargeting(TEXTURE_TO_TARGETING.get(value))
                .build())
        .setVariantTargeting(
            moduleSplit.getVariantTargeting().toBuilder()
                .setTextureCompressionFormatTargeting(TEXTURE_TO_TARGETING.get(value))
                .build())
        .build();
  }

  /**
   * Update the module to remove the specified targeting from the assets - both the directories in
   * assets config and the associated module entries having the specified targeting will be updated.
   */
  public static ModuleSplit removeAssetsTargeting(
      ModuleSplit moduleSplit, TargetingDimension dimension) {
    if (!moduleSplit.getAssetsConfig().isPresent()) {
      return moduleSplit;
    }

    // Update the targeted assets directory and their associated entries.
    Assets assetsConfig = moduleSplit.getAssetsConfig().get();
    Assets.Builder updatedAssetsConfig = assetsConfig.toBuilder().clearDirectory();
    ImmutableList<ModuleEntry> updatedEntries = moduleSplit.getEntries();

    for (TargetedAssetsDirectory targetedAssetsDirectory : assetsConfig.getDirectoryList()) {
      TargetedAssetsDirectory updatedTargetedAssetsDirectory =
          removeAssetsTargetingFromDirectory(targetedAssetsDirectory, dimension);

      // Remove the targeting from the entries path.
      if (!updatedTargetedAssetsDirectory.equals(targetedAssetsDirectory)) {
        // Update the associated entries
        ZipPath directoryPath = ZipPath.create(targetedAssetsDirectory.getPath());
        updatedEntries =
            updatedEntries.stream()
                .map(
                    entry -> {
                      if (entry.getPath().startsWith(directoryPath)) {
                        return removeTargetingFromEntry(entry, dimension);
                      }

                      return entry;
                    })
                .collect(toImmutableList());
      }

      updatedAssetsConfig.addDirectory(updatedTargetedAssetsDirectory);
    }

    return moduleSplit.toBuilder()
        .setEntries(updatedEntries)
        .setAssetsConfig(updatedAssetsConfig.build())
        .build();
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

  /** Checks if any of the targeted directories utilize device tier targeting. */
  public static boolean containsDeviceTierTargeting(
      ImmutableSet<TargetedDirectory> targetedDirectories) {
    return targetedDirectories.stream()
        .map(directory -> directory.getTargeting(TargetingDimension.DEVICE_TIER))
        .anyMatch(Optional::isPresent);
  }

  /**
   * Update the module to remove the specified targeting from the assets: both the assets in assets
   * config and the associated entries having the specified targeting will be updated.
   */
  public static ModuleSplit excludeAssetsTargetingOtherValue(
      ModuleSplit moduleSplit, TargetingDimension dimension, String value) {
    if (!moduleSplit.getAssetsConfig().isPresent()) {
      return moduleSplit;
    }

    // Update the targeted assets directory and their associated entries.
    Assets assetsConfig = moduleSplit.getAssetsConfig().get();
    Assets.Builder updatedAssetsConfig = assetsConfig.toBuilder().clearDirectory();
    ImmutableList<ModuleEntry> updatedEntries = moduleSplit.getEntries();

    for (TargetedAssetsDirectory targetedAssetsDirectory : assetsConfig.getDirectoryList()) {
      ZipPath directoryPath = ZipPath.create(targetedAssetsDirectory.getPath());

      // Check if the directory is targeted at this dimension, but for another value.
      if (isDirectoryTargetingOtherValue(targetedAssetsDirectory, dimension, value)) {
        // Removed the associated entries if so.
        updatedEntries =
            updatedEntries.stream()
                .filter(entry -> !entry.getPath().startsWith(directoryPath))
                .collect(toImmutableList());
      } else {
        // Keep the directory otherwise.
        updatedAssetsConfig.addDirectory(targetedAssetsDirectory);
      }
    }

    return moduleSplit.toBuilder()
        .setEntries(updatedEntries)
        .setAssetsConfig(updatedAssetsConfig.build())
        .build();
  }

  /** Update the directory path and targeting to remove the specified dimension. */
  private static TargetedAssetsDirectory removeAssetsTargetingFromDirectory(
      TargetedAssetsDirectory directory, TargetingDimension dimension) {
    // Only TCF is supported for now in targeting removal.
    checkArgument(dimension.equals(TargetingDimension.TEXTURE_COMPRESSION_FORMAT));

    if (!directory.getTargeting().hasTextureCompressionFormat()) {
      // Return the unmodified, immutable original object if no changes were applied.
      return directory;
    }

    TargetedDirectory targetedDirectory =
        TargetedDirectory.parse(ZipPath.create(directory.getPath()));
    TargetedDirectory newTargetedDirectory = targetedDirectory.removeTargeting(dimension);

    // Note that even if newTargetedDirectory is equal to targetedDirectory, we don't bail out
    // early as the path might not contain a targeting suffix (e.g: "tcf_astc") but still
    // be configured with a targeting.

    // Update the directory path (to use the version with the targeting removed)
    // and update the targeting to remove the dimension.
    return directory.toBuilder()
        .setPath(newTargetedDirectory.toZipPath().toString())
        .setTargeting(directory.getTargeting().toBuilder().clearTextureCompressionFormat().build())
        .build();
  }

  /** Remove the specified dimension from the path represented by the module entry. */
  private static ModuleEntry removeTargetingFromEntry(
      ModuleEntry moduleEntry, TargetingDimension dimension) {
    // Quickly discard path that don't exhibit targeting for the specified dimension.
    if (!TargetedDirectorySegment.pathMayContain(moduleEntry.getPath().toString(), dimension)) {
      return moduleEntry;
    }

    // Rewrite the path with the targeting for the specified dimension removed.
    TargetedDirectory targetedDirectory = TargetedDirectory.parse(moduleEntry.getPath());
    TargetedDirectory newTargetedDirectory = targetedDirectory.removeTargeting(dimension);

    if (!newTargetedDirectory.equals(targetedDirectory)) {
      return moduleEntry.toBuilder().setPath(newTargetedDirectory.toZipPath()).build();
    }

    // Return the unmodified, immutable original object if no changes were applied.
    return moduleEntry;
  }

  /**
   * Checks if the directory is not targeting the specified dimension value (either by not targeting
   * the dimension, or targeting another value).
   */
  private static boolean isDirectoryTargetingOtherValue(
      TargetedAssetsDirectory directory, TargetingDimension dimension, String searchedValue) {
    // Only TCF is supported for now in targeting detection.
    checkArgument(dimension.equals(TargetingDimension.TEXTURE_COMPRESSION_FORMAT));

    AssetsDirectoryTargeting targeting = directory.getTargeting();

    if (!targeting.hasTextureCompressionFormat()) {
      // The directory is not even targeting the specified dimension,
      // so it's not targeting another value for this dimension.
      return false;
    }

    // If no value is specified for this directory, it means that it is a fallback for other
    // sibling directories containing alternative TCFs.
    // Similarly, an empty searched value means that we're looking for fallback directories.
    boolean isDirectoryValueFallback =
        targeting.getTextureCompressionFormat().getValueList().isEmpty();
    boolean isSearchedValueFallback = searchedValue.isEmpty();
    if (isSearchedValueFallback || isDirectoryValueFallback) {
      return isSearchedValueFallback != isDirectoryValueFallback;
    }

    // If a searched value is specified, and the directory has a value for this dimension too,
    // read it and check if it's the same as the searched one.
    String targetingValue =
        TextureCompressionUtils.TARGETING_TO_TEXTURE.getOrDefault(
            Iterables.getOnlyElement(targeting.getTextureCompressionFormat().getValueList())
                .getAlias(),
            null);

    return !searchedValue.equals(targetingValue);
  }
}
