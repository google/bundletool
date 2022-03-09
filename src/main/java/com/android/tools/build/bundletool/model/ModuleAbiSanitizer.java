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

package com.android.tools.build.bundletool.model;

import static com.android.tools.build.bundletool.model.BundleModule.LIB_DIRECTORY;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;

import com.android.bundle.Files.NativeLibraries;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Collection;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Makes sure that each "lib/<ABI>" directory contains the same number of files. If there is a
 * discrepancy, then ony directory(ies) with the most files is/are preserved, and the {@link
 * NativeLibraries} targeting is adjusted appropriately.
 *
 * <p>Reason for existence of this code is that if dependencies of a project contain some native
 * libraries (eg. x86 and x86_64) but the developer only targets x86, then we don't want to generate
 * the x86_64 split at all. If we did generate the x86_64 split, it would be preferred by an x86_64
 * device which would then not get the developer-supplied x86 libraries and crash. This is
 * considered a work-around due to behavior lacking in the build tools. Once build tools handle
 * native libraries correctly, this should be obliterated with fire.
 */
class ModuleAbiSanitizer {

  private static final Logger logger = Logger.getLogger(ModuleAbiSanitizer.class.getName());

  @CheckReturnValue
  public BundleModule sanitize(BundleModule module) {
    // The maps index native files by their containing ABI directory (example key is "lib/x86").
    ImmutableMultimap<ZipPath, ModuleEntry> libFilesByAbiDir = indexLibFilesByAbiDir(module);
    ImmutableMultimap<ZipPath, ModuleEntry> libFilesByAbiDirToKeep =
        discardAbiDirsWithTooFewFiles(libFilesByAbiDir);

    if (libFilesByAbiDirToKeep.size() == libFilesByAbiDir.size()) {
      // ABI directories are correct, nothing to do.
      return module;
    }

    logger.warning(
        String.format(
            "Native directories of module '%s' don't contain the same number of files."
                + " The following files were therefore discarded: %s",
            module.getName(),
            Sets.difference(
                    ImmutableSet.copyOf(libFilesByAbiDir.values()),
                    ImmutableSet.copyOf(libFilesByAbiDirToKeep.values()))
                .stream()
                .map(ModuleEntry::getPath)
                .map(ZipPath::toString)
                .sorted()
                .collect(toImmutableList())));

    return sanitizedModule(module, libFilesByAbiDirToKeep);
  }

  /**
   * Indexes files under "lib/" based on the ABI directory (example keys are "lib/x86", "lib/mips").
   */
  private static ImmutableMultimap<ZipPath, ModuleEntry> indexLibFilesByAbiDir(
      BundleModule module) {
    return module.getEntries().stream()
        .filter(entry -> entry.getPath().startsWith(LIB_DIRECTORY))
        .collect(
            toImmutableSetMultimap(entry -> entry.getPath().subpath(0, 2), Function.identity()));
  }

  private static ImmutableMultimap<ZipPath, ModuleEntry> discardAbiDirsWithTooFewFiles(
      ImmutableMultimap<ZipPath, ModuleEntry> libFilesByAbiDir) {
    int maxAbiFiles =
        libFilesByAbiDir.asMap().values().stream().mapToInt(Collection::size).max().orElse(0);

    // Create a set-based multimap for fast look-ups.
    return ImmutableSetMultimap.copyOf(
        Multimaps.filterKeys(
            libFilesByAbiDir, abiDir -> libFilesByAbiDir.get(abiDir).size() == maxAbiFiles));
  }

  private static BundleModule sanitizedModule(
      BundleModule module, ImmutableMultimap<ZipPath, ModuleEntry> libFilesByAbiDirToKeep) {

    // Construct new module by making minimal modifications to the existing module.
    BundleModule.Builder newModule = module.toBuilder();

    if (module.getNativeConfig().isPresent()) {
      NativeLibraries newNativeConfig =
          filterNativeTargeting(module.getNativeConfig().get(), libFilesByAbiDirToKeep);
      newModule.setNativeConfig(newNativeConfig);
    }

    newModule.setEntryMap(
        module.getEntries().stream()
            .filter(
                entry ->
                    !entry.getPath().startsWith(LIB_DIRECTORY)
                        || libFilesByAbiDirToKeep.containsValue(entry))
            .collect(toImmutableMap(ModuleEntry::getPath, Function.identity())));

    return newModule.build();
  }

  private static NativeLibraries filterNativeTargeting(
      NativeLibraries nativeLibraries,
      ImmutableMultimap<ZipPath, ModuleEntry> preservedEntriesByAbiDir) {

    ImmutableSet<String> preservedAbiDirs =
        preservedEntriesByAbiDir.keySet().stream().map(ZipPath::toString).collect(toImmutableSet());

    return nativeLibraries
        .toBuilder()
        .clearDirectory()
        .addAllDirectory(
            nativeLibraries.getDirectoryList().stream()
                .filter(targetedDirectory -> preservedAbiDirs.contains(targetedDirectory.getPath()))
                .collect(toImmutableList()))
        .build();
  }
}
