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
package com.android.tools.build.bundletool.validation;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Optional;

/** Validator of the BundleConfig. */
public final class BundleConfigValidator extends SubValidator {

  /**
   * Those characters would be technically acceptable in a glob, but don't make much sense for a
   * file in an APK, and are probably an error from the developers.
   */
  private static final ImmutableSet<String> FORBIDDEN_CHARS_IN_GLOB = ImmutableSet.of("\n", "\\\\");

  @Override
  public void validateBundle(AppBundle bundle) {
    BundleConfig bundleConfig = bundle.getBundleConfig();

    validateVersion(bundleConfig);
    validateCompression(bundleConfig.getCompression());
    validateOptimizations(bundleConfig.getOptimizations());
    validateMasterResources(bundleConfig, bundle);
  }

  private void validateCompression(Compression compression) {
    FileSystem fileSystem = FileSystems.getDefault();

    for (String pattern : compression.getUncompressedGlobList()) {
      if (FORBIDDEN_CHARS_IN_GLOB.stream().anyMatch(pattern::contains)) {
        throw ValidationException.builder()
            .withMessage("Invalid uncompressed glob: '%s'.", pattern)
            .build();
      }

      try {
        fileSystem.getPathMatcher("glob:" + pattern);
      } catch (IllegalArgumentException e) {
        throw ValidationException.builder()
            .withCause(e)
            .withMessage("Invalid uncompressed glob: '%s'.", pattern)
            .build();
      }
    }
  }

  private void validateOptimizations(Optimizations optimizations) {
    List<SplitDimension> splitDimensions = optimizations.getSplitsConfig().getSplitDimensionList();

    // We only throw if an unrecognized dimension is enabled, since that would generate an
    // unexpected output. However, we tolerate if the unknown dimension is negated since the output
    // will be the same.
    if (splitDimensions
        .stream()
        .anyMatch(
            dimension ->
                dimension.getValue().equals(Value.UNRECOGNIZED) && !dimension.getNegate())) {
      throw ValidationException.builder()
          .withMessage(
              "BundleConfig.pb contains an unrecognized split dimension. Update bundletool?")
          .build();
    }

    if (splitDimensions.stream().map(SplitDimension::getValueValue).distinct().count()
        != splitDimensions.size()) {
      throw ValidationException.builder()
          .withMessage("BundleConfig.pb contains duplicate split dimensions: %s", splitDimensions)
          .build();
    }
  }

  private void validateVersion(BundleConfig bundleConfig) {
    try {
      BundleToolVersion.getVersionFromBundleConfig(bundleConfig);
    } catch (ValidationException e) {
      throw ValidationException.builder()
          .withCause(e)
          .withMessage("Invalid version in the BundleConfig.pb file.")
          .build();
    }
  }

  private void validateMasterResources(BundleConfig bundleConfig, AppBundle bundle) {
    ImmutableSet<Integer> resourcesToBePinned =
        ImmutableSet.copyOf(bundleConfig.getMasterResources().getResourceIdsList());
    if (resourcesToBePinned.isEmpty()) {
      return;
    }

    ImmutableSet<Integer> allResourceIds =
        bundle.getFeatureModules().values().stream()
            .map(BundleModule::getResourceTable)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(resourceTable -> ResourcesUtils.entries(resourceTable))
            .map(ResourceTableEntry::getResourceId)
            .map(ResourceId::getFullResourceId)
            .collect(toImmutableSet());

    SetView<Integer> undefinedResources = Sets.difference(resourcesToBePinned, allResourceIds);

    if (!undefinedResources.isEmpty()) {
      throw ValidationException.builder()
          .withMessage(
              "Error in BundleConfig. The Master Resources list contains resource IDs not defined "
                  + "in any module. For example: 0x%08x",
              undefinedResources.iterator().next())
          .build();
    }
  }
}
