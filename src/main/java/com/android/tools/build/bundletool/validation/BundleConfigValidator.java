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
import static java.util.stream.Collectors.joining;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Config.Optimizations;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SplitDimension.Value;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.PathMatcher;
import com.android.tools.build.bundletool.model.utils.PathMatcher.GlobPatternSyntaxException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.android.tools.build.bundletool.model.utils.TextureCompressionUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.List;
import java.util.Optional;

/** Validator of the BundleConfig. */
public final class BundleConfigValidator extends SubValidator {

  /**
   * Those characters would be technically acceptable in a glob, but don't make much sense for a
   * file in an APK, and are probably an error from the developers.
   */
  private static final ImmutableSet<String> FORBIDDEN_CHARS_IN_GLOB = ImmutableSet.of("\n", "\\\\");

  private static final ImmutableSet<SplitDimension.Value> SUFFIX_STRIPPING_ENABLED_DIMENSIONS =
      ImmutableSet.of(Value.TEXTURE_COMPRESSION_FORMAT, Value.DEVICE_TIER, Value.COUNTRY_SET);

  @Override
  public void validateBundle(AppBundle bundle) {
    BundleConfig bundleConfig = bundle.getBundleConfig();

    validateVersion(bundleConfig);
    validateCompression(bundleConfig.getCompression());
    validateOptimizations(bundleConfig.getOptimizations());
    validateMasterResources(bundleConfig, bundle);
  }

  public void validateCompression(Compression compression) {
    for (String pattern : compression.getUncompressedGlobList()) {
      if (FORBIDDEN_CHARS_IN_GLOB.stream().anyMatch(pattern::contains)) {
        throw InvalidBundleException.builder()
            .withUserMessage("Invalid uncompressed glob: '%s'.", pattern)
            .build();
      }

      try {
        PathMatcher.createFromGlob(pattern);
      } catch (GlobPatternSyntaxException e) {
        throw InvalidBundleException.builder()
            .withCause(e)
            .withUserMessage("Invalid uncompressed glob: '%s'.", pattern)
            .build();
      }
    }
  }

  private void validateOptimizations(Optimizations optimizations) {
    validateSplitDimensions(optimizations.getSplitsConfig().getSplitDimensionList());
  }

  private void validateSplitDimensions(List<SplitDimension> splitDimensions) {
    // We only throw if an unrecognized dimension is enabled, since that would generate an
    // unexpected output. However, we tolerate if the unknown dimension is negated since the output
    // will be the same.
    if (splitDimensions.stream()
        .anyMatch(
            dimension ->
                dimension.getValue().equals(Value.UNRECOGNIZED) && !dimension.getNegate())) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "BundleConfig.pb contains an unrecognized split dimension. Update bundletool?")
          .build();
    }

    if (splitDimensions.stream().map(SplitDimension::getValueValue).distinct().count()
        != splitDimensions.size()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "BundleConfig.pb contains duplicate split dimensions: %s", splitDimensions)
          .build();
    }

    if (splitDimensions.stream()
        .anyMatch(
            dimension ->
                dimension.hasSuffixStripping()
                    && dimension.getSuffixStripping().getEnabled()
                    && !SUFFIX_STRIPPING_ENABLED_DIMENSIONS.contains(dimension.getValue()))) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Suffix stripping was enabled for an unsupported dimension. Supported dimensions"
                  + " are: %s.",
              SUFFIX_STRIPPING_ENABLED_DIMENSIONS.stream().map(Value::name).collect(joining(", ")))
          .build();
    }

    splitDimensions.stream()
        .filter(dimension -> dimension.getValue().equals(Value.TEXTURE_COMPRESSION_FORMAT))
        .filter(dimension -> !dimension.getSuffixStripping().getDefaultSuffix().isEmpty())
        .filter(
            dimension ->
                !TextureCompressionUtils.TEXTURE_TO_TARGETING.containsKey(
                    dimension.getSuffixStripping().getDefaultSuffix()))
        .findFirst()
        .ifPresent(
            dimension -> {
              ImmutableSet<String> supportedTextures =
                  TextureCompressionUtils.TEXTURE_TO_TARGETING.keySet();

              throw InvalidBundleException.builder()
                  .withUserMessage(
                      "The default texture compression format chosen for suffix stripping (\"%s\") "
                          + "is not valid. Supported formats are: %s.",
                      dimension.getSuffixStripping().getDefaultSuffix(),
                      supportedTextures.stream().collect(joining(", ")))
                  .build();
            });
  }

  private void validateVersion(BundleConfig bundleConfig) {
    try {
      BundleToolVersion.getVersionFromBundleConfig(bundleConfig);
    } catch (IllegalArgumentException e) {
      throw InvalidBundleException.builder()
          .withCause(e)
          .withUserMessage("Invalid version in the BundleConfig.pb file.")
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
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Error in BundleConfig. The Master Resources list contains resource IDs not defined "
                  + "in any module. For example: 0x%08x",
              undefinedResources.iterator().next())
          .build();
    }
  }
}
