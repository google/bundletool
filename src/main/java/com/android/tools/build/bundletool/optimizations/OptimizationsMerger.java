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

import static com.android.bundle.Config.SplitDimension.Value.UNRECOGNIZED;
import static com.android.bundle.Config.SplitDimension.Value.UNSPECIFIED_VALUE;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.utils.EnumMapper;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merger of the optimizations instructions supplied by the developer (in the BundleConfig) and the
 * defaults set by BundleTool.
 */
public final class OptimizationsMerger {

  private static final ImmutableMap<SplitDimension.Value, OptimizationDimension>
      SPLIT_DIMENSION_ENUM_MAP =
          EnumMapper.mapByName(
              SplitDimension.Value.class,
              OptimizationDimension.class,
              /* ignoreValues= */ ImmutableSet.of(UNRECOGNIZED, UNSPECIFIED_VALUE));

  /**
   * Merges the optimizations instructions supplied by the developer (in the BundleConfig) and the
   * defaults set by BundleTool.
   */
  public ApkOptimizations mergeWithDefaults(BundleConfig bundleConfig) {
    return mergeWithDefaults(bundleConfig, ImmutableSet.of());
  }

  /**
   * Merges the optimizations instructions supplied by the developer (in the BundleConfig), the
   * defaults set by BundleTool and the values provided in the command.
   *
   * <p>If {@code optimizationsOverride} is not empty, we only apply these optimizations. Otherwise,
   * we use the default optimizations merged with the overrides specified in the BundleConfig.
   */
  @Deprecated // Optimization flags will go away soon!
  public ApkOptimizations mergeWithDefaults(
      BundleConfig bundleConfig, ImmutableSet<OptimizationDimension> optimizationsOverride) {
    // Default optimizations performed on APKs if the developer doesn't specify any preferences.
    String buildVersionString = bundleConfig.getBundletool().getVersion();
    Version bundleToolBuildVersion =
        buildVersionString.isEmpty()
            ? BundleToolVersion.getCurrentVersion()
            : Version.of(buildVersionString);
    ApkOptimizations defaultOptimizations =
        ApkOptimizations.getDefaultOptimizationsForVersion(bundleToolBuildVersion);

    // Preferences specified by the developer.
    Optimizations requestedOptimizations = bundleConfig.getOptimizations();

    // Until we get rid of OptimizationsOverride flag, it takes precedence over anything else.
    ImmutableSet<OptimizationDimension> splitDimensions =
        !optimizationsOverride.isEmpty()
            ? optimizationsOverride
            : mergeSplitDimensions(
                defaultOptimizations.getSplitDimensions(),
                requestedOptimizations.getSplitsConfig().getSplitDimensionList());

    // If developer sets UncompressNativeLibraries use that, otherwise use the default value.
    boolean uncompressNativeLibraries =
        requestedOptimizations.hasUncompressNativeLibraries()
            ? requestedOptimizations.getUncompressNativeLibraries().getEnabled()
            : defaultOptimizations.getUncompressNativeLibraries();

    boolean uncompressDexFiles = requestedOptimizations.getUncompressDexFiles().getEnabled();

    return ApkOptimizations.builder()
        .setSplitDimensions(splitDimensions)
        .setUncompressNativeLibraries(uncompressNativeLibraries)
        .setUncompressDexFiles(uncompressDexFiles)
        .build();
  }


  private ImmutableSet<OptimizationDimension> mergeSplitDimensions(
      ImmutableSet<OptimizationDimension> defaultSplitDimensions,
      List<SplitDimension> requestedSplitDimensions) {
    Set<OptimizationDimension> mergedDimensions = new HashSet<>(defaultSplitDimensions);

    for (SplitDimension requestedSplitDimension : requestedSplitDimensions) {
      OptimizationDimension internalDimension =
          SPLIT_DIMENSION_ENUM_MAP.get(requestedSplitDimension.getValue());
      if (requestedSplitDimension.getNegate()) {
        mergedDimensions.remove(internalDimension);
      } else {
        mergedDimensions.add(internalDimension);
      }
    }

    return ImmutableSet.copyOf(mergedDimensions);
  }
  
}
