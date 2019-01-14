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
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_M_API_VERSION;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.List;

/** Modifies a module by setting the compression of of native libraries based on SDK version. */
public final class NativeLibrariesCompressionSplitter implements ModuleSplitSplitter {

  private final ApkGenerationConfiguration apkGenerationConfiguration;

  @VisibleForTesting
  NativeLibrariesCompressionSplitter() {
    this(ApkGenerationConfiguration.getDefaultInstance());
  }

  public NativeLibrariesCompressionSplitter(ApkGenerationConfiguration apkGenerationConfiguration) {
    this.apkGenerationConfiguration = apkGenerationConfiguration;
  }

  /**
   * Generates a single {@link ModuleSplit} setting the compression on native libraries.
   *
   * <p>Native libraries are only compressed for non instant apps on pre M devices.
   */
  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit moduleSplit) {
    checkState(
        !moduleSplit.getApkTargeting().hasSdkVersionTargeting(),
        "Split already targets SDK version.");

    if (!moduleSplit.getNativeConfig().isPresent()) {
      return ImmutableList.of(moduleSplit);
    }

    // Flatten all targeted directories.
    List<TargetedNativeDirectory> allTargetedDirectories =
        moduleSplit.getNativeConfig().get().getDirectoryList();

    ImmutableSet<ModuleEntry> libraryEntries =
        allTargetedDirectories
            .stream()
            .flatMap(directory -> moduleSplit.findEntriesUnderPath(directory.getPath()))
            .collect(toImmutableSet());

    // Only the split APKs targeting devices below Android M should be compressed. Instant apps
    // always support uncompressed native libraries (even on Android L), because they are not always
    // executed by the Android platform.
    boolean shouldCompress =
        !apkGenerationConfiguration.isForInstantAppVariants() && targetsPreM(moduleSplit);

    return ImmutableList.of(
        createModuleSplit(
            moduleSplit,
            mergeAndSetCompression(libraryEntries, moduleSplit, shouldCompress),
            /* extractNativeLibs= */ shouldCompress));
  }

  private static boolean targetsPreM(ModuleSplit moduleSplit) {
    int sdkVersion =
        Iterables.getOnlyElement(
                moduleSplit.getVariantTargeting().getSdkVersionTargeting().getValueList())
            .getMin()
            .getValue();

    return sdkVersion < ANDROID_M_API_VERSION;
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
      ImmutableList<ModuleEntry> moduleEntries,
      boolean extractNativeLibs) {
    return moduleSplit
        .toBuilder()
        .addMasterManifestMutator(withExtractNativeLibs(extractNativeLibs))
        .setEntries(moduleEntries)
        .build();
  }
}
