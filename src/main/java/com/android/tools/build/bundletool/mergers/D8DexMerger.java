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

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExistsAndEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.ThrowableUtils;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** Merges dex files using D8. */
public class D8DexMerger implements DexMerger {

  private static final String DEX_OVERFLOW_MSG =
      "Cannot fit requested classes in a single dex file";

  @Override
  public ImmutableList<Path> merge(
      ImmutableList<Path> dexFiles,
      Path outputDir,
      Optional<Path> mainDexListFile,
      boolean isDebuggable,
      int minSdkVersion) {

    try {
      validateInput(dexFiles, outputDir);

      // Many of the D8 parameters are not being set because those are used when compiling into dex,
      // however we are merging existing dex files. The parameters considered are:
      // - classpathFiles, libraryFiles: Required for desugaring during compilation.
      D8Command.Builder command =
          D8Command.builder()
              .setOutput(outputDir, OutputMode.DexIndexed)
              .addProgramFiles(dexFiles)
              .setMinApiLevel(minSdkVersion)
              // Compilation mode affects whether D8 produces minimal main-dex.
              // In debug mode minimal main-dex is always produced, so that the validity of the
              // main-dex can be debugged. For release mode minimal main-dex is not produced and the
              // primary dex file will be filled as appropriate.
              .setMode(isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE);
      mainDexListFile.ifPresent(command::addMainDexListFiles);

      // D8 throws when main dex list is not provided and the merge result doesn't fit into a single
      // dex file.
      D8.run(command.build());

      File[] mergedFiles = outputDir.toFile().listFiles();

      return Arrays.stream(mergedFiles).map(File::toPath).collect(toImmutableList());

    } catch (CompilationFailedException e) {
      throw translateD8Exception(e);
    }
  }

  private static void validateInput(ImmutableList<Path> dexFiles, Path outputDir) {
    checkDirectoryExistsAndEmpty(outputDir);
    dexFiles.forEach(FilePreconditions::checkFileExistsAndReadable);
  }

  private static CommandExecutionException translateD8Exception(
      CompilationFailedException d8Exception) {
    // DexOverflowException is thrown when the merged result doesn't fit into a single dex file and
    // `mainDexClasses` is empty. Detection of the exception in the stacktrace is non-trivial and at
    // the time of writing this code it is suppressed exception of the root cause.
    if (ThrowableUtils.anyInCausalChainOrSuppressedMatches(
        d8Exception, t -> t.getMessage() != null && t.getMessage().contains(DEX_OVERFLOW_MSG))) {
      return new CommandExecutionException(
          "Dex merging failed because the result does not fit into a single dex file and"
              + " multidex is not supported by the input.",
          d8Exception);
    } else {
      return new CommandExecutionException("Dex merging failed.", d8Exception);
    }
  }
}
