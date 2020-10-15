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
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_N_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_P_API_VERSION;
import static com.android.tools.build.bundletool.splitters.NativeLibrariesHelper.mayHaveNativeActivities;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.List;

/** Modifies a module by setting the compression of of native libraries based on SDK version. */
public final class NativeLibrariesCompressionSplitter implements ModuleSplitSplitter {

  private final ApkGenerationConfiguration apkGenerationConfiguration;

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
        allTargetedDirectories.stream()
            .flatMap(directory -> moduleSplit.findEntriesUnderPath(directory.getPath()))
            .collect(toImmutableSet());

    boolean forceUncompressed = supportUncompressedNativeLibs(moduleSplit);
    return ImmutableList.of(
        createModuleSplit(
            moduleSplit,
            mergeAndSetCompression(libraryEntries, moduleSplit, forceUncompressed),
            /* extractNativeLibs= */ !forceUncompressed));
  }

  private boolean supportUncompressedNativeLibs(ModuleSplit moduleSplit) {
    // If uncompressed native libraries are disabled for bundle we should compress them for all
    // variants.
    if (!apkGenerationConfiguration.getEnableUncompressedNativeLibraries()) {
      return false;
    }
    // Instant apps support uncompressed native libs since Android L.
    if (apkGenerationConfiguration.isForInstantAppVariants()) {
      return true;
    }
    // Persistent apps target at least Android P support uncompressed native libraries without any
    // restrictions.
    if (targetsAtLeast(ANDROID_P_API_VERSION, moduleSplit)) {
      return true;
    }

    // Persistent apps target Android N+ can use uncompressed native libraries in case they are
    // are not installable on external storage as uncompressed native libraries crash with ASEC
    // external storage and support for ASEC external storage is removed in Android P.
    if (targetsAtLeast(ANDROID_N_API_VERSION, moduleSplit)) {
      return !apkGenerationConfiguration.isInstallableOnExternalStorage();
    }

    // Persistent apps with uncompressed libraries are supported on Android M if they are
    // not installable on external storage and do not use native activities because native
    // activities with uncompressed native libs crash on Android M b/145808311.
    if (targetsAtLeast(ANDROID_M_API_VERSION, moduleSplit)) {
      return !apkGenerationConfiguration.isInstallableOnExternalStorage()
          && !mayHaveNativeActivities(moduleSplit);
    }

    return false;
  }

  private static boolean targetsAtLeast(int version, ModuleSplit moduleSplit) {
    return getSdkVersion(moduleSplit) >= version;
  }

  private static int getSdkVersion(ModuleSplit moduleSplit) {
    return Iterables.getOnlyElement(
            moduleSplit.getVariantTargeting().getSdkVersionTargeting().getValueList())
        .getMin()
        .getValue();
  }

  private ImmutableList<ModuleEntry> mergeAndSetCompression(
      ImmutableSet<ModuleEntry> libraryEntries,
      ModuleSplit moduleSplit,
      boolean forceUncompressed) {

    ImmutableSet<ModuleEntry> nonLibraryEntries =
        moduleSplit.getEntries().stream()
            .filter(entry -> !libraryEntries.contains(entry))
            .collect(toImmutableSet());

    return ImmutableList.<ModuleEntry>builder()
        .addAll(
            libraryEntries.stream()
                .map(
                    moduleEntry ->
                        moduleEntry.toBuilder().setForceUncompressed(forceUncompressed).build())
                .collect(toImmutableList()))
        .addAll(nonLibraryEntries)
        .build();
  }

  private ModuleSplit createModuleSplit(
      ModuleSplit moduleSplit,
      ImmutableList<ModuleEntry> moduleEntries,
      boolean extractNativeLibs) {
    return moduleSplit.toBuilder()
        .addMasterManifestMutator(withExtractNativeLibs(extractNativeLibs))
        .setEntries(moduleEntries)
        .build();
  }
}
