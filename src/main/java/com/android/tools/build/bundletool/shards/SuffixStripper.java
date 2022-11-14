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

package com.android.tools.build.bundletool.shards;

import static com.android.tools.build.bundletool.model.utils.TextureCompressionUtils.TEXTURE_TO_TARGETING;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.CountrySetTargeting;
import com.android.bundle.Targeting.DeviceTierTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectory;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectorySegment;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.protobuf.Int32Value;

/**
 * Strips suffixes on a module for a given targeting dimension.
 *
 * <p>There are two parts to suffix stripping:
 *
 * <ul>
 *   <li>Remove the suffixes from the paths of each module entry, and their respective targeting.
 *   <li>For standalone and universal APKs, remove all entries with suffixes that are not the
 *       default suffix.
 * </ul>
 */
public final class SuffixStripper {
  private final TargetingDimension targetingDimension;
  private final TargetingDimensionHandler dimensionHandler;

  public static SuffixStripper createForDimension(SplitDimension.Value dimension) {
    switch (dimension) {
      case TEXTURE_COMPRESSION_FORMAT:
        return createForDimension(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
      case DEVICE_TIER:
        return createForDimension(TargetingDimension.DEVICE_TIER);
      case COUNTRY_SET:
        return createForDimension(TargetingDimension.COUNTRY_SET);
      default:
        throw new IllegalArgumentException("Cannot strip suffixes for dimension " + dimension);
    }
  }

  public static SuffixStripper createForDimension(TargetingDimension dimension) {
    switch (dimension) {
      case TEXTURE_COMPRESSION_FORMAT:
        return new SuffixStripper(dimension, new TextureCompressionFormatDimensionHandler());
      case DEVICE_TIER:
        return new SuffixStripper(dimension, new DeviceTierDimensionHandler());
      case COUNTRY_SET:
        return new SuffixStripper(dimension, new CountrySetDimensionHandler());
      default:
        throw new IllegalArgumentException("Cannot strip suffixes for dimension " + dimension);
    }
  }

  private SuffixStripper(TargetingDimension dimension, TargetingDimensionHandler dimensionHandler) {
    this.targetingDimension = dimension;
    this.dimensionHandler = dimensionHandler;
  }

  /**
   * Applies the given {@link SuffixStripping} to a {@link ModuleSplit}.
   *
   * <p>This will remove all assets that target a suffix other than the default and strip the suffix
   * from the remaining ones. The Apk and Variant targeting for this module will be updated to the
   * default value..
   */
  public ModuleSplit applySuffixStripping(ModuleSplit split, SuffixStripping suffixStripping) {
    // Only keep assets for the selected dimension value.
    split = excludeAssetsTargetingOtherValue(split, suffixStripping.getDefaultSuffix());

    // Strip the targeting from the asset paths if suffix stripping is enabled.
    if (suffixStripping.getEnabled()) {
      split = removeAssetsTargeting(split);
    }

    // Apply the updated targeting to the module split (as it now only contains assets for
    // the selected targeting value)
    split = setTargetingByDefaultSuffix(split, suffixStripping.getDefaultSuffix());

    return split;
  }

  /**
   * Updates the module to have the targeting specified by a default value for a dimension. This
   * means that the Apk and Variant targeting for this module will have the value passed for this
   * dimension - or none if the value is empty.
   */
  private ModuleSplit setTargetingByDefaultSuffix(ModuleSplit moduleSplit, String value) {
    // If the value is empty, we don't need to modify the targeting of the module split.
    if (value.isEmpty()) {
      return moduleSplit;
    }

    // Apply the updated targeting to the module split (as it now only contains assets for
    // the selected TCF), both for the APK and the variant targeting.
    return moduleSplit.toBuilder()
        .setApkTargeting(
            dimensionHandler.setTargetingDimension(moduleSplit.getApkTargeting(), value))
        .setVariantTargeting(
            dimensionHandler.setTargetingDimension(moduleSplit.getVariantTargeting(), value))
        .build();
  }

  /**
   * Updates the module to remove the specified targeting from the assets - both the directories in
   * assets config and the associated module entries having the specified targeting will be updated.
   */
  public ModuleSplit removeAssetsTargeting(ModuleSplit moduleSplit) {
    if (!moduleSplit.getAssetsConfig().isPresent()) {
      return moduleSplit;
    }

    // Update the targeted assets directory and their associated entries.
    Assets assetsConfig = moduleSplit.getAssetsConfig().get();
    Assets.Builder updatedAssetsConfig = assetsConfig.toBuilder().clearDirectory();
    ImmutableList<ModuleEntry> updatedEntries = moduleSplit.getEntries();

    for (TargetedAssetsDirectory targetedAssetsDirectory : assetsConfig.getDirectoryList()) {
      TargetedAssetsDirectory updatedTargetedAssetsDirectory =
          removeAssetsTargetingFromDirectory(targetedAssetsDirectory);

      // Remove the targeting from the entries path.
      if (!updatedTargetedAssetsDirectory.equals(targetedAssetsDirectory)) {
        // Update the associated entries
        ZipPath directoryPath = ZipPath.create(targetedAssetsDirectory.getPath());
        updatedEntries =
            updatedEntries.stream()
                .map(
                    entry -> {
                      if (entry.getPath().startsWith(directoryPath)) {
                        return removeTargetingFromEntry(entry);
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
   * Updates the module to remove the specified targeting from the assets: both the assets in assets
   * config and the associated entries having the specified targeting will be updated.
   */
  private ModuleSplit excludeAssetsTargetingOtherValue(ModuleSplit moduleSplit, String value) {
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
      if (dimensionHandler.isDirectoryTargetingOtherValue(targetedAssetsDirectory, value)) {
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

  /** Updates the directory path and targeting to remove the specified dimension. */
  private TargetedAssetsDirectory removeAssetsTargetingFromDirectory(
      TargetedAssetsDirectory directory) {

    if (!dimensionHandler.hasTargetingDimension(directory.getTargeting())) {
      // Return the unmodified, immutable original object if no changes were applied.
      return directory;
    }

    TargetedDirectory targetedDirectory =
        TargetedDirectory.parse(ZipPath.create(directory.getPath()));
    TargetedDirectory newTargetedDirectory = targetedDirectory.removeTargeting(targetingDimension);

    // Note that even if newTargetedDirectory is equal to targetedDirectory, we don't bail out
    // early as the path might not contain a targeting suffix (e.g: "tcf_astc") but still
    // be configured with a targeting.

    // Update the directory path (to use the version with the targeting removed)
    // and update the targeting to remove the dimension.
    return directory.toBuilder()
        .setPath(newTargetedDirectory.toZipPath().toString())
        .setTargeting(dimensionHandler.clearTargetingDimension(directory.getTargeting()))
        .build();
  }

  /** Removes the specified dimension from the path represented by the module entry. */
  private ModuleEntry removeTargetingFromEntry(ModuleEntry moduleEntry) {
    // Quickly discard path that don't exhibit targeting for the specified dimension.
    if (!TargetedDirectorySegment.pathMayContain(
        moduleEntry.getPath().toString(), targetingDimension)) {
      return moduleEntry;
    }

    // Rewrite the path with the targeting for the specified dimension removed.
    TargetedDirectory targetedDirectory = TargetedDirectory.parse(moduleEntry.getPath());
    TargetedDirectory newTargetedDirectory = targetedDirectory.removeTargeting(targetingDimension);

    if (!newTargetedDirectory.equals(targetedDirectory)) {
      return moduleEntry.toBuilder().setPath(newTargetedDirectory.toZipPath()).build();
    }

    // Return the unmodified, immutable original object if no changes were applied.
    return moduleEntry;
  }

  /** Provides functionality for handling a particular targeting dimension. */
  private interface TargetingDimensionHandler {

    /** Checks if the given {@link AssetsDirectoryTargeting} targets the handled dimension. */
    boolean hasTargetingDimension(AssetsDirectoryTargeting directoryTargeting);

    /**
     * Removes the targeting for the handled dimension in the given {@link
     * AssetsDirectoryTargeting}.
     */
    AssetsDirectoryTargeting clearTargetingDimension(AssetsDirectoryTargeting directoryTargeting);

    /** Sets a targeting value for the handled dimension in the given {@link ApkTargeting}. */
    ApkTargeting setTargetingDimension(ApkTargeting apkTargeting, String value);

    /** Sets a targeting value for the handled dimension in the given {@link VariantTargeting}. */
    VariantTargeting setTargetingDimension(VariantTargeting variantTargeting, String value);

    /**
     * Checks if the directory is not targeting the specified dimension value (either by not
     * targeting the handled dimension, or targeting another value).
     */
    boolean isDirectoryTargetingOtherValue(TargetedAssetsDirectory directory, String searchedValue);
  }

  /** {@link TargetingDimensionHandler} for texture compression format targeting. */
  private static final class TextureCompressionFormatDimensionHandler
      implements TargetingDimensionHandler {
    @Override
    public boolean hasTargetingDimension(AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.hasTextureCompressionFormat();
    }

    @Override
    public AssetsDirectoryTargeting clearTargetingDimension(
        AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.toBuilder().clearTextureCompressionFormat().build();
    }

    @Override
    public ApkTargeting setTargetingDimension(ApkTargeting apkTargeting, String value) {
      return apkTargeting.toBuilder()
          .setTextureCompressionFormatTargeting(TEXTURE_TO_TARGETING.get(value))
          .build();
    }

    @Override
    public VariantTargeting setTargetingDimension(VariantTargeting variantTargeting, String value) {
      return variantTargeting.toBuilder()
          .setTextureCompressionFormatTargeting(TEXTURE_TO_TARGETING.get(value))
          .build();
    }

    @Override
    public boolean isDirectoryTargetingOtherValue(
        TargetedAssetsDirectory directory, String searchedValue) {
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

  /** {@link TargetingDimensionHandler} for device tier targeting. */
  private static final class DeviceTierDimensionHandler implements TargetingDimensionHandler {
    @Override
    public boolean hasTargetingDimension(AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.hasDeviceTier();
    }

    @Override
    public AssetsDirectoryTargeting clearTargetingDimension(
        AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.toBuilder().clearDeviceTier().build();
    }

    @Override
    public ApkTargeting setTargetingDimension(ApkTargeting apkTargeting, String value) {
      return apkTargeting.toBuilder()
          .setDeviceTierTargeting(
              DeviceTierTargeting.newBuilder().addValue(Int32Value.of(Integer.parseInt(value))))
          .build();
    }

    @Override
    public VariantTargeting setTargetingDimension(VariantTargeting variantTargeting, String value) {
      // No device tier variant targeting.
      return variantTargeting;
    }

    @Override
    public boolean isDirectoryTargetingOtherValue(
        TargetedAssetsDirectory directory, String searchedValue) {
      AssetsDirectoryTargeting targeting = directory.getTargeting();

      if (!targeting.hasDeviceTier()) {
        // The directory is not even targeting the specified dimension,
        // so it's not targeting another value for this dimension.
        return false;
      }

      // If the searched value (default tier) is empty, we default it to "0", which is the standard
      // default when the developer doesn't provide an override.
      String searchedValueWithDefault = searchedValue.isEmpty() ? "0" : searchedValue;

      String targetingValue =
          Integer.toString(
              Iterables.getOnlyElement(targeting.getDeviceTier().getValueList()).getValue());
      return !searchedValueWithDefault.equals(targetingValue);
    }
  }

  /** {@link TargetingDimensionHandler} for country set targeting. */
  private static final class CountrySetDimensionHandler implements TargetingDimensionHandler {
    @Override
    public boolean hasTargetingDimension(AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.hasCountrySet();
    }

    @Override
    public AssetsDirectoryTargeting clearTargetingDimension(
        AssetsDirectoryTargeting directoryTargeting) {
      return directoryTargeting.toBuilder().clearCountrySet().build();
    }

    @Override
    public ApkTargeting setTargetingDimension(ApkTargeting apkTargeting, String value) {
      return apkTargeting.toBuilder()
          .setCountrySetTargeting(CountrySetTargeting.newBuilder().addValue(value))
          .build();
    }

    @Override
    public VariantTargeting setTargetingDimension(VariantTargeting variantTargeting, String value) {
      // No country set variant targeting.
      return variantTargeting;
    }

    @Override
    public boolean isDirectoryTargetingOtherValue(
        TargetedAssetsDirectory directory, String searchedValue) {
      AssetsDirectoryTargeting targeting = directory.getTargeting();

      if (!targeting.hasCountrySet()) {
        // The directory is not even targeting the specified dimension,
        // so it's not targeting another value for this dimension.
        return false;
      }

      // The value list can be empty for default directory for country sets.
      // In which case, the country set name is "", targeting all other countries.
      String targetingValue =
          Iterables.getOnlyElement(targeting.getCountrySet().getValueList(), "");
      return !searchedValue.equals(targetingValue);
    }
  }
}
