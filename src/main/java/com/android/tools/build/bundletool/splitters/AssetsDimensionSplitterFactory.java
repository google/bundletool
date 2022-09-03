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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.ManifestMutator.withSplitsRequired;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.android.tools.build.bundletool.shards.SuffixStripper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.protobuf.Message;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A Factory for creating asset splitters processing a single dimension.
 *
 * <p>The splitters created by this class traverse a set of directory groups and generate a separate
 * module split for each unique dimension value across the targeted directories. This is done
 * separately for each directory group.
 *
 * <p>This may create splits with identical targeting. However, it is by design for simplicity in
 * mind as during the normal course of module splitting, the {@link SameTargetingMerger} will unify
 * splits across directory groups if applicable, hence this is not done here.
 *
 * <p>The total number of generated splits equals the number of distinct targeting dimension values
 * summed across each directory group and summed together. An absent targeting on a dimension of the
 * splitter in the targeted directory counts as a distinct dimension value for the purpose of the
 * above summation.
 */
public class AssetsDimensionSplitterFactory {
  /**
   * Creates a {@link ModuleSplitSplitter} capable of splitting on a given Asset targeting
   * dimension.
   *
   * See {@link #createSplitter(Function, Function, Predicate, Optional)} for parameters
   * descriptions.
   */
  public static <T extends Message> ModuleSplitSplitter createSplitter(
      Function<AssetsDirectoryTargeting, T> dimensionGetter,
      Function<T, ApkTargeting> targetingSetter,
      Predicate<ApkTargeting> hasTargeting) {
    return createSplitter(
        dimensionGetter,
        targetingSetter,
        hasTargeting,
        /* targetingDimensionToRemove= */ Optional.empty());
  }

  /**
   * Creates a {@link ModuleSplitSplitter} capable of splitting on a given Asset targeting
   * dimension, with, optionally, a dimension to be removed from asset paths.
   *
   * @param <T> the proto buffer message class representing the splitting targeting dimension.
   * @param dimensionGetter function that extracts the sub-message representing a targeting
   *     dimension.
   * @param targetingSetter function that creates a split targeting that will be merged with the
   *     targeting of the input {@link ModuleSplit}.
   * @param hasTargeting predicate to test if the input {@link ModuleSplit} is already targeting on
   *     the dimension of this splitter.
   * @param targetingDimensionToRemove If not empty, the targeting for this dimension will be
   * removed from asset paths (i.e: suffixes like #tcf_xxx will be removed from paths).
   * @return {@link ModuleSplitSplitter} for a given dimension functions.
   */
  public static <T extends Message> ModuleSplitSplitter createSplitter(
      Function<AssetsDirectoryTargeting, T> dimensionGetter,
      Function<T, ApkTargeting> targetingSetter,
      Predicate<ApkTargeting> hasTargeting,
      Optional<TargetingDimension> targetingDimensionToRemove) {
    return new ModuleSplitSplitter() {

      @Override
      public ImmutableCollection<ModuleSplit> split(ModuleSplit split) {
        checkArgument(
            !hasTargeting.test(split.getApkTargeting()),
            "Split is already targeting the splitting dimension.");

        return split
            .getAssetsConfig()
            .map(assetsConfig -> splitAssetsDirectories(assetsConfig, split))
            .orElse(ImmutableList.of(split))
            .stream()
            .map(
                moduleSplit ->
                    moduleSplit.isMasterSplit() ? moduleSplit : removeAssetsTargeting(moduleSplit))
            .collect(toImmutableList());
      }

      private ModuleSplit removeAssetsTargeting(ModuleSplit split) {
        return targetingDimensionToRemove.isPresent()
            ? SuffixStripper.createForDimension(targetingDimensionToRemove.get())
                .removeAssetsTargeting(split)
            : split;
      }

      private ImmutableList<ModuleSplit> splitAssetsDirectories(Assets assets, ModuleSplit split) {
        Multimap<T, TargetedAssetsDirectory> directoriesMap =
            Multimaps.filterKeys(
                Multimaps.index(
                    assets.getDirectoryList(),
                    targetedDirectory -> dimensionGetter.apply(targetedDirectory.getTargeting())),
                not(this::isDefaultTargeting));
        ImmutableList.Builder<ModuleSplit> splitsBuilder = new ImmutableList.Builder<>();
        // Generate config splits.
        directoriesMap
            .asMap()
            .entrySet()
            .forEach(
                entry -> {
                  ImmutableList<ModuleEntry> entries =
                      listEntriesFromDirectories(entry.getValue(), split);
                  if (entries.isEmpty()) {
                    return;
                  }
                  ModuleSplit.Builder modifiedSplit = split.toBuilder();

                  modifiedSplit
                      .setEntries(entries)
                      .setApkTargeting(generateTargeting(split.getApkTargeting(), entry.getKey()))
                      .setMasterSplit(false)
                      .addMasterManifestMutator(withSplitsRequired(true));

                  splitsBuilder.add(modifiedSplit.build());
                });
        // Ensure that master split (even an empty one) always exists.
        ModuleSplit defaultSplit = getDefaultAssetsSplit(split, splitsBuilder.build());
        if (defaultSplit.isMasterSplit() || !defaultSplit.getEntries().isEmpty()) {
          splitsBuilder.add(defaultSplit);
        }
        return splitsBuilder.build();
      }

      private ModuleSplit getDefaultAssetsSplit(
          ModuleSplit inputSplit, ImmutableList<ModuleSplit> configSplits) {
        ImmutableSet<ModuleEntry> claimedEntries =
            configSplits.stream()
                .map(ModuleSplit::getEntries)
                .flatMap(Collection::stream)
                .collect(toImmutableSet());
        return inputSplit.toBuilder()
            .setEntries(
                inputSplit.getEntries().stream()
                    .filter(not(claimedEntries::contains))
                    .collect(toImmutableList()))
            .build();
      }

      private boolean isDefaultTargeting(T splittingDimensionTargeting) {
        return splittingDimensionTargeting.equals(
            splittingDimensionTargeting.getDefaultInstanceForType());
      }

      private ApkTargeting generateTargeting(ApkTargeting splitTargeting, T extraTargeting) {
        if (isDefaultTargeting(extraTargeting)) {
          return splitTargeting;
        }
        return splitTargeting.toBuilder().mergeFrom(targetingSetter.apply(extraTargeting)).build();
      }

      private ImmutableList<ModuleEntry> listEntriesFromDirectories(
          Collection<TargetedAssetsDirectory> directories, ModuleSplit moduleSplit) {
        return directories.stream()
            .map(targetedAssetsDirectory -> ZipPath.create(targetedAssetsDirectory.getPath()))
            .flatMap(moduleSplit::getEntriesInDirectory)
            .collect(toImmutableList());
      }
    };
  }

  // Do not instantiate.
  private AssetsDimensionSplitterFactory() {}
}
