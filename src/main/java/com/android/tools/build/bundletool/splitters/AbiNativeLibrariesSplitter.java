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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toList;

import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import java.util.List;

/** Splits the native libraries in the module by ABI. */
public class AbiNativeLibrariesSplitter implements ModuleSplitSplitter {

  /** Generates {@link ModuleSplit} objects dividing the native libraries by ABI. */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    if (!moduleSplit.getNativeConfig().isPresent()) {
      return ImmutableList.of(moduleSplit);
    }

    ImmutableList.Builder<ModuleSplit> splits = new ImmutableList.Builder<>();
    // Flatten all targeted directories.
    List<TargetedNativeDirectory> allTargetedDirectories =
        moduleSplit.getNativeConfig().get().getDirectoryList();
    // Currently we only support targeting via ABI, so grouping it by Targeting.equals() should be
    // enough.
    ImmutableMultimap<NativeDirectoryTargeting, TargetedNativeDirectory> targetingMap =
        Multimaps.index(allTargetedDirectories, TargetedNativeDirectory::getTargeting);
    ImmutableSet<Abi> allAbis =
        targetingMap
            .keySet()
            .stream()
            .map(NativeDirectoryTargeting::getAbi)
            .collect(toImmutableSet());
    for (NativeDirectoryTargeting targeting : targetingMap.keySet()) {
      ImmutableList.Builder<ModuleEntry> entriesList =
          new ImmutableList.Builder<ModuleEntry>()
              .addAll(
                  targetingMap
                      .get(targeting)
                      .stream()
                      .flatMap(directory -> moduleSplit.findEntriesUnderPath(directory.getPath()))
                      .collect(toList()));

      ModuleSplit.Builder splitBuilder =
          moduleSplit
              .toBuilder()
              .setTargeting(
                  moduleSplit
                      .getTargeting()
                      .toBuilder()
                      .setAbiTargeting(
                          AbiTargeting.newBuilder()
                              .addValue(targeting.getAbi())
                              .addAllAlternatives(
                                  Sets.difference(allAbis, ImmutableSet.of(targeting.getAbi()))))
                      .build())
              .setMasterSplit(false)
              .setEntries(entriesList.build());
      splits.add(splitBuilder.build());
    }
    return splits.build();
  }
}
