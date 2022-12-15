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
package com.android.tools.build.bundletool.optimizations;

import static com.android.tools.build.bundletool.model.OptimizationDimension.ABI;
import static com.android.tools.build.bundletool.model.OptimizationDimension.COUNTRY_SET;
import static com.android.tools.build.bundletool.model.OptimizationDimension.DEVICE_TIER;
import static com.android.tools.build.bundletool.model.OptimizationDimension.LANGUAGE;
import static com.android.tools.build.bundletool.model.OptimizationDimension.SCREEN_DENSITY;
import static com.android.tools.build.bundletool.model.OptimizationDimension.TEXTURE_COMPRESSION_FORMAT;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Immutable;

/** Optimizations that should be performed on the generated APKs. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class ApkOptimizations {

  /**
   * List of optimizations performed on APKs keyed by BundleTool version where they were introduced.
   *
   * <p>When we introduce new optimizations, this allows us to enable them for developers using the
   * latest version of BundleTool and not affecting developers who built the bundle using an older
   * version of BundleTool.
   */
  private static final ImmutableSortedMap<Version, ApkOptimizations>
      DEFAULT_OPTIMIZATIONS_BY_BUNDLETOOL_VERSION =
          ImmutableSortedMap.<Version, ApkOptimizations>naturalOrder()
              .put(
                  Version.of("0.0.0-dev"),
                  ApkOptimizations.builder()
                      .setSplitDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE))
                      .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                      .build())
              .put(
                  Version.of("0.6.0"),
                  ApkOptimizations.builder()
                      .setSplitDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY, LANGUAGE))
                      .setUncompressNativeLibraries(true)
                      .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                      .build())
              .put(
                  Version.of("0.10.2"),
                  ApkOptimizations.builder()
                      .setSplitDimensions(
                          ImmutableSet.of(
                              ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, LANGUAGE))
                      .setUncompressNativeLibraries(true)
                      .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                      .build())
              .put(
                  Version.of("1.11.3"),
                  ApkOptimizations.builder()
                      .setSplitDimensions(
                          ImmutableSet.of(
                              ABI, SCREEN_DENSITY, TEXTURE_COMPRESSION_FORMAT, LANGUAGE))
                      .setUncompressNativeLibraries(true)
                      .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                      .setUncompressDexFiles(true)
                      .setUncompressedDexTargetSdk(UncompressedDexTargetSdk.SDK_31)
                      .build())
              .put(
                  Version.of("1.13.2"),
                  ApkOptimizations.builder()
                      .setSplitDimensions(
                          ImmutableSet.of(
                              ABI,
                              SCREEN_DENSITY,
                              TEXTURE_COMPRESSION_FORMAT,
                              LANGUAGE,
                              DEVICE_TIER))
                      .setUncompressNativeLibraries(true)
                      .setStandaloneDimensions(ImmutableSet.of(ABI, SCREEN_DENSITY))
                      .setUncompressDexFiles(true)
                      .setUncompressedDexTargetSdk(UncompressedDexTargetSdk.SDK_31)
                      .build())
              .buildOrThrow();

  /** List of dimensions supported by asset modules. */
  private static final ImmutableSet<OptimizationDimension> DIMENSIONS_SUPPORTED_BY_ASSET_MODULES =
      ImmutableSet.of(LANGUAGE, TEXTURE_COMPRESSION_FORMAT, DEVICE_TIER, COUNTRY_SET);

  public abstract ImmutableSet<OptimizationDimension> getSplitDimensions();

  public ImmutableSet<OptimizationDimension> getSplitDimensionsForAssetModules() {
    return Sets.intersection(getSplitDimensions(), DIMENSIONS_SUPPORTED_BY_ASSET_MODULES)
        .immutableCopy();
  }

  public abstract boolean getUncompressNativeLibraries();

  public abstract boolean getUncompressDexFiles();

  public abstract UncompressedDexTargetSdk getUncompressedDexTargetSdk();

  public abstract ImmutableSet<OptimizationDimension> getStandaloneDimensions();

  public abstract ImmutableMap<OptimizationDimension, SuffixStripping> getSuffixStrippings();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_ApkOptimizations.Builder()
        .setUncompressNativeLibraries(false)
        .setUncompressDexFiles(false)
        .setUncompressedDexTargetSdk(UncompressedDexTargetSdk.UNSPECIFIED)
        .setSuffixStrippings(ImmutableMap.of());
  }

  /** Builder for the {@link ApkOptimizations} class. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSplitDimensions(ImmutableSet<OptimizationDimension> splitDimensions);

    public abstract Builder setUncompressNativeLibraries(boolean enable);

    public abstract Builder setUncompressDexFiles(boolean enable);

    public abstract Builder setUncompressedDexTargetSdk(
        UncompressedDexTargetSdk uncompressedDexTargetSdk);

    public abstract Builder setStandaloneDimensions(
        ImmutableSet<OptimizationDimension> standaloneDimensions);

    public abstract Builder setSuffixStrippings(
        ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings);

    public abstract ApkOptimizations build();
  }

  /**
   * Returns the default optimizations to perform on the generated APKs given the version of
   * BundleTool used to build the App Bundle.
   *
   * <p>The default optimizations are optimizations performed if the developer hasn't provided any
   * other instructions.
   */
  public static ApkOptimizations getDefaultOptimizationsForVersion(Version bundleToolVersion) {
    // We use the default optimizations of the highest version that is below or equal to the build
    // version.
    return checkNotNull(
            DEFAULT_OPTIMIZATIONS_BY_BUNDLETOOL_VERSION.floorEntry(bundleToolVersion),
            "No default optimizations found for BundleTool version %s.",
            bundleToolVersion)
        .getValue();
  }

  /** Returns an optimizations specific to the universal APK. */
  public static ApkOptimizations getOptimizationsForUniversalApk() {
    // Currently no optimizations are performed.
    return ApkOptimizations.builder()
        .setSplitDimensions(ImmutableSet.of())
        .setStandaloneDimensions(ImmutableSet.of())
        .build();
  }
}
