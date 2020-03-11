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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Function.identity;

import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.stream.Stream;

/** Splits the APEX images in the module by ABI. */
public class AbiApexImagesSplitter implements ModuleSplitSplitter {

  /** Generates {@link ModuleSplit} objects dividing the APEX images by ABI. */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    if (!moduleSplit.isApex()) {
      return ImmutableList.of(moduleSplit);
    }

    List<TargetedApexImage> allTargetedImages = moduleSplit.getApexConfig().get().getImageList();

    // A set of all MultiAbis (flattened for repeated values) for easy generation of alternatives.
    ImmutableSet<MultiAbi> allTargeting =
        allTargetedImages.stream()
            .flatMap(image -> image.getTargeting().getMultiAbi().getValueList().stream())
            .collect(toImmutableSet());

    // This prevents O(n^2).
    ImmutableMap<String, ModuleEntry> apexPathToEntryMap =
        buildApexPathToEntryMap(allTargetedImages, moduleSplit);

    ImmutableList.Builder<ModuleSplit> splits = new ImmutableList.Builder<>();
    for (TargetedApexImage targetedApexImage : allTargetedImages) {
      ModuleEntry entry = apexPathToEntryMap.get(targetedApexImage.getPath());
      List<MultiAbi> targeting = targetedApexImage.getTargeting().getMultiAbi().getValueList();
      ModuleSplit.Builder splitBuilder =
          moduleSplit.toBuilder()
              .setApkTargeting(
                  moduleSplit.getApkTargeting().toBuilder()
                      .setMultiAbiTargeting(
                          MultiAbiTargeting.newBuilder()
                              .addAllValue(targeting)
                              .addAllAlternatives(
                                  Sets.difference(allTargeting, ImmutableSet.copyOf(targeting))))
                      .build())
              .setMasterSplit(false)
              .setEntries(
                  targetedApexImage.getBuildInfoPath().isEmpty()
                      ? ImmutableList.of(entry)
                      : ImmutableList.of(
                          entry, apexPathToEntryMap.get(targetedApexImage.getBuildInfoPath())));
      splits.add(splitBuilder.build());
    }

    return splits.build();
  }

  private static ImmutableMap<String, ModuleEntry> buildApexPathToEntryMap(
      List<TargetedApexImage> allTargetedImages, ModuleSplit moduleSplit) {
    ImmutableMap<String, ModuleEntry> pathToEntry =
        Maps.uniqueIndex(moduleSplit.getEntries(), entry -> entry.getPath().toString());

    return Stream.concat(
            allTargetedImages.stream().map(TargetedApexImage::getPath),
            allTargetedImages.stream()
                .map(TargetedApexImage::getBuildInfoPath)
                .filter(p -> !p.isEmpty()))
        .collect(toImmutableMap(identity(), pathToEntry::get));
  }
}
