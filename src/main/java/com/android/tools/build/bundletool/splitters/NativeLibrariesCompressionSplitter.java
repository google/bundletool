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

import static com.android.tools.build.bundletool.model.ManifestMutator.withExtractNativeLibs;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_M_API_VERSION;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;

/** Splits the native libraries in the module by their Compression. */
public class NativeLibrariesCompressionSplitter implements ModuleSplitSplitter {

  /** Generates {@link ModuleSplit} objects dividing the native libraries by their compression. */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    checkState(
        !moduleSplit.getApkTargeting().hasSdkVersionTargeting(),
        "Split already targets SDK version.");

    if (!moduleSplit.getNativeConfig().isPresent()) {
      return ImmutableList.of(moduleSplit);
    }

    ImmutableList.Builder<ModuleSplit> splits = new ImmutableList.Builder<>();

    // Flatten all targeted directories.
    List<TargetedNativeDirectory> allTargetedDirectories =
        moduleSplit.getNativeConfig().get().getDirectoryList();

    ImmutableSet<ModuleEntry> libraryEntries =
        allTargetedDirectories
            .stream()
            .flatMap(directory -> moduleSplit.findEntriesUnderPath(directory.getPath()))
            .collect(toImmutableSet());

    splits.add(
        createModuleSplit(
            moduleSplit,
            sdkVersionTargeting(
                sdkVersionFrom(ANDROID_M_API_VERSION),
                targetsPreM(moduleSplit)
                    ? ImmutableSet.of(sdkVersionFrom(ANDROID_L_API_VERSION))
                    : ImmutableSet.of()),
            mergeAndSetCompression(libraryEntries, moduleSplit, /* shouldCompress= */ false),
            /* extractNativeLibs= */ false));

    if (targetsPreM(moduleSplit)) {
      splits.add(
          createModuleSplit(
              moduleSplit,
              sdkVersionTargeting(
                  sdkVersionFrom(ANDROID_L_API_VERSION),
                  ImmutableSet.of(sdkVersionFrom(ANDROID_M_API_VERSION))),
              mergeAndSetCompression(libraryEntries, moduleSplit, /* shouldCompress= */ true),
              /* extractNativeLibs= */ true));
    }

    return splits.build();
  }

  private static boolean targetsPreM(ModuleSplit moduleSplit) {
    int baseMinSdkVersion = moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion();
    return baseMinSdkVersion < ANDROID_M_API_VERSION;
  }

  private ImmutableList<ModuleEntry> mergeAndSetCompression(
      ImmutableSet<ModuleEntry> libraryEntries, ModuleSplit moduleSplit, boolean shouldCompress) {

    ImmutableSet<ModuleEntry> nonLibraryEntries =
        moduleSplit
            .getEntries()
            .stream()
            .filter(entry -> !libraryEntries.contains(entry))
            .collect(toImmutableSet());

    return ImmutableList.<ModuleEntry>builder()
        .addAll(
            libraryEntries
                .stream()
                .map(moduleEntry -> moduleEntry.setCompression(shouldCompress))
                .collect(toImmutableList()))
        .addAll(nonLibraryEntries)
        .build();
  }

  private ModuleSplit createModuleSplit(
      ModuleSplit moduleSplit,
      SdkVersionTargeting variantSdkVersionTargeting,
      ImmutableList<ModuleEntry> moduleEntries,
      boolean extractNativeLibs) {
    return moduleSplit
        .toBuilder()
        .setMasterManifestMutators(ImmutableList.of(withExtractNativeLibs(extractNativeLibs)))
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
