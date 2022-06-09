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

import com.android.tools.build.bundletool.commands.BuildApksModule.VerboseLogs;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.ThrowableUtils;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer.ForwardingConsumer;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import javax.inject.Inject;

/** Merges dex files using D8. */
public class D8DexMerger implements DexMerger {
  private static final String CORE_DESUGARING_PREFIX = "j$.";
  private static final String CORE_DESUGARING_LIBRARY_EXCEPTION =
      "Merging dex file containing classes with prefix 'j$.'";
  private static final String CORE_DESUGARING_LIBRARY_EXCEPTION_NEW =
      "Merging DEX file containing classes with prefix 'j$.'";
  private static final String DEX_OVERFLOW_MSG =
      "Cannot fit requested classes in a single dex file";

  private final boolean verbose;

  @Inject
  D8DexMerger(@VerboseLogs boolean verbose) {
    this.verbose = verbose;
  }

  D8DexMerger() {
    this(false);
  }

  @Override
  public ImmutableList<Path> merge(
      ImmutableList<Path> dexFiles,
      Path outputDir,
      Optional<Path> mainDexListFile,
      Optional<Path> proguardMap,
      boolean isDebuggable,
      int minSdkVersion) {

    try {
      validateInput(dexFiles, outputDir);

      // Many of the D8 parameters are not being set because those are used when compiling into dex,
      // however we are merging existing dex files. The parameters considered are:
      // - classpathFiles, libraryFiles: Required for desugaring during compilation.
      D8Command.Builder command =
          D8Command.builder(
                  new DiagnosticsHandler() {
                    @Override
                    public void error(Diagnostic error) {
                      if (isCoreDesugaringMessage(error.getDiagnosticMessage())) {
                        return;
                      }
                      DiagnosticsHandler.super.error(error);
                    }

                    @Override
                    public void warning(Diagnostic warning) {
                      if (verbose) {
                        DiagnosticsHandler.super.warning(warning);
                      }
                    }

                    @Override
                    public void info(Diagnostic info) {
                      if (verbose) {
                        DiagnosticsHandler.super.info(info);
                      }
                    }
                  })
              .setOutput(outputDir, OutputMode.DexIndexed)
              .addProgramFiles(dexFiles)
              .setMinApiLevel(minSdkVersion)
              // Compilation mode affects whether D8 produces minimal main-dex.
              // In debug mode minimal main-dex is always produced, so that the validity of the
              // main-dex can be debugged. For release mode minimal main-dex is not produced and the
              // primary dex file will be filled as appropriate.
              .setMode(isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE);
      mainDexListFile.ifPresent(command::addMainDexListFiles);
      proguardMap.ifPresent(command::setProguardInputMapFile);
      // D8 throws when main dex list is not provided and the merge result doesn't fit into a single
      // dex file or when the mapping file passed is invalid.
      D8.run(command.build());

      File[] mergedFiles = outputDir.toFile().listFiles();

      return Arrays.stream(mergedFiles).map(File::toPath).collect(toImmutableList());

    } catch (CompilationFailedException e) {
      // Make sure to delete entries in the output dir.
      cleanupOutputDir(outputDir);
      if (isCoreDesugaringException(e)) {
        // If merge fails because of core desugaring library, exclude dex files related to core
        // desugaring lib and try again.
        return mergeAppDexFilesAndRenameCoreDesugaringDex(
            dexFiles, outputDir, mainDexListFile, proguardMap, isDebuggable, minSdkVersion);
      }
      if (proguardMap.isPresent()) {
        // Try without the proguard map.
        return merge(
            dexFiles, outputDir, mainDexListFile, Optional.empty(), isDebuggable, minSdkVersion);
      } else {
        throw translateD8Exception(e);
      }
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
      return CommandExecutionException.builder()
          .withInternalMessage(
              "Dex merging failed because the result does not fit into a single dex file and"
                  + " multidex is not supported by the input.")
          .withCause(d8Exception)
          .build();
    } else {
      Throwable rootCause = Throwables.getRootCause(d8Exception);
      return CommandExecutionException.builder()
          .withInternalMessage("Dex merging failed. %s", rootCause.getMessage())
          .withCause(d8Exception)
          .build();
    }
  }

  private static boolean isCoreDesugaringException(CompilationFailedException d8Exception) {
    return ThrowableUtils.anyInCausalChainOrSuppressedMatches(
        d8Exception, t -> t.getMessage() != null && isCoreDesugaringMessage(t.getMessage()));
  }

  private static boolean isCoreDesugaringMessage(String message) {
    return message.contains(CORE_DESUGARING_LIBRARY_EXCEPTION)
        || message.contains(CORE_DESUGARING_LIBRARY_EXCEPTION_NEW);
  }

  private ImmutableList<Path> mergeAppDexFilesAndRenameCoreDesugaringDex(
      ImmutableList<Path> dexFiles,
      Path outputDir,
      Optional<Path> mainDexListFile,
      Optional<Path> proguardMap,
      boolean isDebuggable,
      int minSdkVersion) {
    ImmutableList<Path> desugaringDexFiles =
        dexFiles.stream().filter(D8DexMerger::isCoreDesugaringDex).collect(toImmutableList());
    ImmutableList<Path> appDexFiles =
        dexFiles.stream()
            .filter(dex -> !desugaringDexFiles.contains(dex))
            .collect(toImmutableList());

    ImmutableList<Path> mergedAppDexFiles =
        merge(appDexFiles, outputDir, mainDexListFile, proguardMap, isDebuggable, minSdkVersion);
    ImmutableList<Path> mergedDesugaringDexFiles =
        Streams.mapWithIndex(
                desugaringDexFiles.stream(),
                (dex, index) ->
                    copyDexToOutput(dex, outputDir, (int) index + 1 + mergedAppDexFiles.size()))
            .collect(toImmutableList());

    return ImmutableList.<Path>builder()
        .addAll(mergedAppDexFiles)
        .addAll(mergedDesugaringDexFiles)
        .build();
  }

  private static Path copyDexToOutput(Path input, Path outputDir, int index) {
    String outputName = index == 1 ? "classes.dex" : String.format("classes%d.dex", index);
    Path output = outputDir.resolve(outputName);
    try {
      Files.copy(input, output);
      return output;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isCoreDesugaringDex(Path dexFile) {
    try {
      boolean[] isDesugaringDex = new boolean[] {false};
      D8Command.Builder builder =
          D8Command.builder()
              .addProgramFiles(dexFile)
              .setProgramConsumer(new ForwardingConsumer(null));
      builder.addOutputInspection(
          inspection ->
              inspection.forEachClass(
                  clazz ->
                      isDesugaringDex[0] =
                          isDesugaringDex[0]
                              || clazz
                                  .getClassReference()
                                  .getTypeName()
                                  .startsWith(CORE_DESUGARING_PREFIX)));
      D8.run(builder.build());
      return isDesugaringDex[0];
    } catch (CompilationFailedException e) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Failed to read dex file %s.", dexFile.getFileName().toString())
          .withCause(e)
          .build();
    }
  }

  private static void cleanupOutputDir(Path outputDir) {
    try {
      MoreFiles.deleteDirectoryContents(outputDir, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (IOException e1) {
      throw new UncheckedIOException(e1);
    }
  }
}
