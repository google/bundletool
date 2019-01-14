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
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.protobuf.Message;
import java.util.Collection;
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
   * @param <T> the proto buffer message class representing the splitting targeting dimension.
   * @param dimensionGetter function that extracts the sub-message representing a targeting
   *     dimension.
   * @param targetingSetter function that creates a split targeting that will be merged with the
   *     targeting of the input {@link ModuleSplit}.
   * @param hasTargeting predicate to test if the input {@link ModuleSplit} is already targeting on
   *     the dimension of this splitter.
   * @return {@link ModuleSplitSplitter} for a given dimension functions.
   */
  public static <T extends Message> ModuleSplitSplitter createSplitter(
      Function<AssetsDirectoryTargeting, T> dimensionGetter,
      Function<T, ApkTargeting> targetingSetter,
      Predicate<ApkTargeting> hasTargeting) {
    return new ModuleSplitSplitter() {

      @Override
      public ImmutableCollection<ModuleSplit> split(ModuleSplit split) {
        checkArgument(
            !hasTargeting.test(split.getApkTargeting()),
            "Split is already targeting the splitting dimension.");

        return split
            .getAssetsConfig()
            .map(assetsConfig -> splitAssetsDirectories(assetsConfig, split))
            .orElse(ImmutableList.of(split));
      }

      private ImmutableList<ModuleSplit> splitAssetsDirectories(Assets assets, ModuleSplit split) {
        Multimap<T, TargetedAssetsDirectory> directoriesMap =
            Multimaps.index(
                assets.getDirectoryList(),
                targetedDirectory -> dimensionGetter.apply(targetedDirectory.getTargeting()));
        return directoriesMap.asMap().entrySet().stream()
            .map(
                entry -> {
                  ModuleSplit.Builder modifiedSplit = split.toBuilder();

                  boolean isMasterSplit =
                      split.isMasterSplit() && isDefaultTargeting(entry.getKey());

                  modifiedSplit
                      .setEntries(findEntriesInDirectories(entry.getValue(), split))
                      .setApkTargeting(generateTargeting(split.getApkTargeting(), entry.getKey()))
                      .setMasterSplit(isMasterSplit);
                  if (!isMasterSplit) {
                    modifiedSplit.addMasterManifestMutator(withSplitsRequired(true));
                  }

                  return modifiedSplit.build();
                })
            .filter(moduleSplit -> !moduleSplit.getEntries().isEmpty())
            .collect(toImmutableList());
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

      private ImmutableList<ModuleEntry> findEntriesInDirectories(
          Collection<TargetedAssetsDirectory> directories, ModuleSplit moduleSplit) {
        return directories.stream()
            .flatMap(directory -> moduleSplit.findEntriesInsideDirectory(directory.getPath()))
            .collect(toImmutableList());
      }
    };
  }

  // Do not instantiate.
  private AssetsDimensionSplitterFactory() {}
}
