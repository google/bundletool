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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;

import com.android.bundle.Files.NativeLibraries;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableCollection;
import java.util.List;
import java.util.Optional;

/**
 * Helper to share native libraries related logic between NativeLibrariesCompressionSplitter and
 * NativeLibsCompressionVariantGenerator.
 */
public class NativeLibrariesHelper {

  /**
   * In case native activity does not declare 'android.app.lib_name' <meta-data> in
   * AndroidManifex.xml it means that it will use 'main' library
   */
  private static final String MAIN_NATIVE_LIBRARY = "libmain.so";

  public static boolean mayHaveNativeActivities(ModuleSplit moduleSplit) {
    return mayHaveNativeActivities(
        moduleSplit.getAndroidManifest(), moduleSplit.getNativeConfig(), moduleSplit.getEntries());
  }

  public static boolean mayHaveNativeActivities(BundleModule module) {
    return mayHaveNativeActivities(
        module.getAndroidManifest(), module.getNativeConfig(), module.getEntries());
  }

  /**
   * Native activities are activities that use or extend android.app.NativeActivity class.
   *
   * <p>Native activities may explicitly specify native library they are using by providing
   * 'android.app.lib_name' <meta-data>. In case this meta-data is not provided native activity will
   * use main ('libmain.so') library.
   */
  private static boolean mayHaveNativeActivities(
      AndroidManifest manifest,
      Optional<NativeLibraries> nativeConfig,
      ImmutableCollection<ModuleEntry> entries) {
    return manifest.hasExplicitlyDefinedNativeActivities() || hasMainLibrary(nativeConfig, entries);
  }

  private static boolean hasMainLibrary(
      Optional<NativeLibraries> nativeConfig, ImmutableCollection<ModuleEntry> entries) {
    List<ZipPath> nativeLibPaths =
        stream(nativeConfig)
            .flatMap(config -> config.getDirectoryList().stream())
            .map(directory -> ZipPath.create(directory.getPath()))
            .collect(toImmutableList());

    return entries.stream()
        .anyMatch(
            entry ->
                entry.getPath().endsWith(MAIN_NATIVE_LIBRARY)
                    && nativeLibPaths.stream().anyMatch(entry.getPath()::startsWith));
  }

  private NativeLibrariesHelper() {}
}
