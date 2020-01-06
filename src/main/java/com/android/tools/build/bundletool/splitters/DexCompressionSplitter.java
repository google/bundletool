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

import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/** Modifies a module by setting the compression of dex entries based on SDK version. */
public class DexCompressionSplitter implements ModuleSplitSplitter {

  /**
   * Generates a single {@link ModuleSplit} setting the compression of dex entries.
   *
   * <p>Dex entries are only compressed for pre P devices.
   */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    ImmutableSet<ModuleEntry> dexEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> entry.getPath().startsWith(DEX_DIRECTORY))
            .collect(toImmutableSet());

    if (dexEntries.isEmpty()) {
      return ImmutableList.of(moduleSplit);
    }

    // Only APKs targeting devices below Android Q should be compressed.
    boolean shouldCompress = targetsPreQ(moduleSplit);
    return ImmutableList.of(
        createModuleSplit(
            moduleSplit, mergeAndSetCompression(dexEntries, moduleSplit, shouldCompress)));
  }

  private static ImmutableList<ModuleEntry> mergeAndSetCompression(
      ImmutableSet<ModuleEntry> dexEntries, ModuleSplit moduleSplit, boolean shouldCompress) {

    ImmutableSet<ModuleEntry> nonDexEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> !dexEntries.contains(entry))
            .collect(toImmutableSet());

    return ImmutableList.<ModuleEntry>builder()
        .addAll(
            dexEntries.stream()
                .map(moduleEntry -> moduleEntry.setCompression(shouldCompress))
                .collect(toImmutableList()))
        .addAll(nonDexEntries)
        .build();
  }

  private static boolean targetsPreQ(ModuleSplit moduleSplit) {
    int sdkVersion =
        Iterables.getOnlyElement(
                moduleSplit.getVariantTargeting().getSdkVersionTargeting().getValueList())
            .getMin()
            .getValue();

    return sdkVersion < ANDROID_Q_API_VERSION;
  }

  private static ModuleSplit createModuleSplit(
      ModuleSplit moduleSplit,
      ImmutableList<ModuleEntry> moduleEntries) {
    return moduleSplit
        .toBuilder()
        .setEntries(moduleEntries)
        .build();
  }
}
