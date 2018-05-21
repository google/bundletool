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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.version.BundleToolVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    ImmutableList<SplitDimension.Value> duplicateSplitDimensions =
        optimizations
            // Construct Map<SplitDimension.Value, Long> that counts dimension occurrences.
            .getSplitsConfig()
            .getSplitDimensionList()
            .stream()
            .map(SplitDimension::getValue)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            // Find dimension(s) occurring more than once.
            .entrySet()
            .stream()
            .filter(mapEntry -> mapEntry.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(toImmutableList());
    if (!duplicateSplitDimensions.isEmpty()) {
      throw ValidationException.builder()
          .withMessage(
              "BundleConfig.pb contains duplicate split dimensions: %s", duplicateSplitDimensions)
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
}
