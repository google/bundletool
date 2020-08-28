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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.shards.BundleModule64BitNativeLibrariesRemover;
import com.google.common.collect.ImmutableCollection;
import java.io.PrintStream;
import java.util.Optional;
import javax.inject.Inject;

/**
 * {@link AppBundlePreprocessor} that filters bundle entries that contain 64 bit libraries if the
 * bundle contains any renderscript code.
 *
 * <p>If the Platform finds renderscript code in the app, it will run it in 32 bit mode, so the 64
 * bit libraries will not be used and can be removed.
 */
public class AppBundle64BitNativeLibrariesPreprocessor implements AppBundlePreprocessor {

  private final BundleModule64BitNativeLibrariesRemover bundleModule64BitNativeLibrariesRemover;
  private final Optional<PrintStream> logPrintStream;

  @Inject
  AppBundle64BitNativeLibrariesPreprocessor(
      BundleModule64BitNativeLibrariesRemover bundleModule64BitNativeLibrariesRemover,
      Optional<PrintStream> logPrintStream) {
    this.bundleModule64BitNativeLibrariesRemover = bundleModule64BitNativeLibrariesRemover;
    this.logPrintStream = logPrintStream;
  }

  @Override
  public AppBundle preprocess(AppBundle originalBundle) {
    boolean filter64BitLibraries = has32BitRenderscriptCode(originalBundle);

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

  public ImmutableCollection<BundleModule> processModules(
      ImmutableCollection<BundleModule> modules) {
    return modules.stream()
        .map(bundleModule64BitNativeLibrariesRemover::strip64BitLibraries)
        .collect(toImmutableList());
  }

  private static boolean has32BitRenderscriptCode(AppBundle bundle) {
    return bundle.getFeatureModules().values().stream()
        .anyMatch(BundleModule::hasRenderscript32Bitcode);
  }

  private void printWarning(String message) {
    logPrintStream.ifPresent(out -> out.println("WARNING: " + message));
  }
}
