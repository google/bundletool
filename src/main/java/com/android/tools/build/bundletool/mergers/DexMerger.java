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

package com.android.tools.build.bundletool.mergers;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

/** Merges dex files. */
public interface DexMerger {

  /**
   * Merges dex files possibly using a main dex list, and writes the result to the given directory.
   *
   * <p>If the merging results in more than one dex file and {@code mainDexClasses} is empty, the
   * merging fails with an exception.
   *
   * @param outputDir an empty directory to write the merged dex files into
   * @param mainDexListFile file containing names of classes that need to be in the primary dex
   *     file. Specified using format "com/example/MyClass.class", one class name per line.
   * @param isDebuggable indicates whether the Android app has the 'debuggable' flag set
   * @return merged dex files
   * @throws com.android.tools.build.bundletool.model.exceptions.CommandExecutionException on
   *     failure
   */
  ImmutableList<Path> merge(
      ImmutableList<Path> dexFiles,
      Path outputDir,
      Optional<Path> mainDexListFile,
      Optional<Path> proguardMap,
      boolean isDebuggable,
      int minSdkVersion);
}
