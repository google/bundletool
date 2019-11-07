/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.preprocessors;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import java.io.PrintStream;
import java.util.Optional;

/**
 * {@link AppBundlePreprocessor} that filters bundle entries that contain 64 bit libraries if the
 * bundle contains any renderscript code.
 *
 * <p>If the Platform finds renderscript code in the app, it will run it in 32 bit mode, so the 64
 * bit libraries will not be used and can be removed.
 */
public class AppBundle64BitNativeLibrariesPreprocessor implements AppBundlePreprocessor {

  private final Optional<PrintStream> logPrintStream;

  public AppBundle64BitNativeLibrariesPreprocessor(Optional<PrintStream> logPrintStream) {
    this.logPrintStream = logPrintStream;
  }

  @Override
  public AppBundle preprocess(AppBundle originalBundle) {
    boolean filter64BitLibraries = originalBundle.has32BitRenderscriptCode();

    if (!filter64BitLibraries) {
      return originalBundle;
    }
    printWarning(
        "App Bundle contains 32-bit RenderScript bitcode file (.bc) which disables 64-bit "
            + "support in Android. 64-bit native libraries won't be included in generated "
            + "APKs.");
    return originalBundle.toBuilder()
        .setRawModules(processModules(originalBundle.getModules().values()))
        .build();
  }

  public static ImmutableCollection<BundleModule> processModules(
      ImmutableCollection<BundleModule> modules) {
    return modules.stream()
        .map(AppBundle64BitNativeLibrariesPreprocessor::processModule)
        .collect(toImmutableList());
  }

  private static BundleModule processModule(BundleModule module) {
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
      throw CommandExecutionException.builder()
          .withMessage(
              "Usage of 64-bit native libraries is disabled by the presence of a "
                  + "renderscript file, but App Bundle contains only 64-bit native libraries.")
          .build();
    }

    return module.toBuilder()
        .setRawEntries(processEntries(module.getEntries(), dirsToRemove))
        .setNativeConfig(processTargeting(nativeConfig.get(), dirsToRemove))
        .build();
  }

  private static ImmutableCollection<ModuleEntry> processEntries(
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
        .filter(AppBundle64BitNativeLibrariesPreprocessor::targets64BitAbi)
        .collect(toImmutableSet());
  }

  private static NativeLibraries processTargeting(
      NativeLibraries nativeConfig, ImmutableSet<TargetedNativeDirectory> dirsToRemove) {
    return nativeConfig.toBuilder()
        .clearDirectory()
        .addAllDirectory(
            nativeConfig.getDirectoryList().stream()
                .filter(not(dirsToRemove::contains))
                .collect(toImmutableList()))
        .build();
  }

  private static boolean targets64BitAbi(TargetedNativeDirectory targetedNativeDirectory) {
    return targetedNativeDirectory.getTargeting().hasAbi()
        && is64Bit(targetedNativeDirectory.getTargeting().getAbi());
  }

  private static boolean is64Bit(Abi abi) {
    return AbiName.fromProto(abi.getAlias()).getBitSize() == 64;
  }

  private void printWarning(String message) {
    logPrintStream.ifPresent(out -> out.println("WARNING: " + message));
  }
}
