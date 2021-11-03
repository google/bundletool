/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Optional;
import javax.inject.Inject;

/** Removes 64-bit libraries from bundle modules. */
public class BundleModule64BitNativeLibrariesRemover {

  @Inject
  public BundleModule64BitNativeLibrariesRemover() {}

  /** Creates a new bundle module with removed 64-bit libraries from original module. */
  @CheckReturnValue
  public BundleModule strip64BitLibraries(BundleModule module) {
    Optional<NativeLibraries> nativeConfig = module.getNativeConfig();
    if (!nativeConfig.isPresent()) {
      return module;
    }

    ImmutableSet<TargetedNativeDirectory> dirsToRemove =
        get64BitTargetedNativeDirectories(nativeConfig.get());
    if (dirsToRemove.isEmpty()) {
      return module;
    }
    if (dirsToRemove.size() == nativeConfig.get().getDirectoryCount()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Usage of 64-bit native libraries is disabled, but App Bundle contains only 64-bit"
                  + " native libraries. This usually happens if you have included 32 bit "
                  + "RenderScript files (.bc), but only have 64-bit native code.")
          .build();
    }

    return module.toBuilder()
        .setRawEntries(filterEntries(module.getEntries(), dirsToRemove))
        .setNativeConfig(filterTargeting(nativeConfig.get(), dirsToRemove))
        .build();
  }

  private static ImmutableCollection<ModuleEntry> filterEntries(
      ImmutableCollection<ModuleEntry> entries,
      ImmutableCollection<TargetedNativeDirectory> targeted64BitNativeDirectories) {
    return entries.stream()
        .filter(entry -> shouldIncludeEntry(entry, targeted64BitNativeDirectories))
        .collect(toImmutableList());
  }

  private static boolean shouldIncludeEntry(
      ModuleEntry entry,
      ImmutableCollection<TargetedNativeDirectory> targeted64BitNativeDirectories) {
    return targeted64BitNativeDirectories.stream()
        .noneMatch(
            targetedNativeDirectory ->
                entry.getPath().startsWith(targetedNativeDirectory.getPath()));
  }

  private static ImmutableSet<TargetedNativeDirectory> get64BitTargetedNativeDirectories(
      NativeLibraries nativeLibraries) {
    return nativeLibraries.getDirectoryList().stream()
        .filter(BundleModule64BitNativeLibrariesRemover::targets64BitAbi)
        .collect(toImmutableSet());
  }

  private static boolean targets64BitAbi(TargetedNativeDirectory targetedNativeDirectory) {
    return targetedNativeDirectory.getTargeting().hasAbi()
        && is64Bit(targetedNativeDirectory.getTargeting().getAbi());
  }

  private static boolean is64Bit(Abi abi) {
    return AbiName.fromProto(abi.getAlias()).getBitSize() == 64;
  }

  private static NativeLibraries filterTargeting(
      NativeLibraries nativeConfig, ImmutableSet<TargetedNativeDirectory> dirsToRemove) {
    return nativeConfig.toBuilder()
        .clearDirectory()
        .addAllDirectory(
            nativeConfig.getDirectoryList().stream()
                .filter(not(dirsToRemove::contains))
                .collect(toImmutableList()))
        .build();
  }
}
