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
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_P_API_VERSION;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/** Splits the dex files in the module by their Compression. */
public class DexCompressionSplitter implements ModuleSplitSplitter {

  /** Generates {@link ModuleSplit} objects dividing the dex files by their compression. */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    ImmutableSet<ModuleEntry> dexEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> entry.getPath().startsWith(DEX_DIRECTORY))
            .collect(toImmutableSet());

    if (dexEntries.isEmpty()) {
      return ImmutableList.of(moduleSplit);
    }

    ImmutableList.Builder<ModuleSplit> splits = new ImmutableList.Builder<>();
    splits.add(
        createModuleSplit(
            moduleSplit,
            sdkVersionTargeting(
                sdkVersionFrom(ANDROID_P_API_VERSION),
                targetsPreP(moduleSplit)
                    ? ImmutableSet.of(sdkVersionFrom(ANDROID_L_API_VERSION))
                    : ImmutableSet.of()),
            mergeAndSetCompression(dexEntries, moduleSplit, /* shouldCompress= */ false)));

    if (targetsPreP(moduleSplit)) {
      splits.add(
          createModuleSplit(
              moduleSplit,
              sdkVersionTargeting(
                  sdkVersionFrom(ANDROID_L_API_VERSION),
                  ImmutableSet.of(sdkVersionFrom(ANDROID_P_API_VERSION))),
              mergeAndSetCompression(dexEntries, moduleSplit, /* shouldCompress= */ true)));
    }

    return splits.build();
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

  private static boolean targetsPreP(ModuleSplit moduleSplit) {
    int minSdkVersion = moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion();
    return minSdkVersion < ANDROID_P_API_VERSION;
  }

  private static ModuleSplit createModuleSplit(
      ModuleSplit moduleSplit,
      SdkVersionTargeting variantSdkVersionTargeting,
      ImmutableList<ModuleEntry> moduleEntries) {
    return moduleSplit
        .toBuilder()
        .setVariantTargeting(
            moduleSplit
                .getVariantTargeting()
                .toBuilder()
                .setSdkVersionTargeting(variantSdkVersionTargeting)
                .build())
        .setEntries(moduleEntries)
        .build();
  }
}
