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
package com.android.tools.build.bundletool.preprocessors;

import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import javax.inject.Inject;

/**
 * Preprocessor that overrides the compression for module entries.
 *
 * <p>Asset module entries are uncompressed.
 */
public class EntryCompressionPreprocessor implements AppBundlePreprocessor {

  private final ModuleCompressionManager moduleCompressionManager;

  @Inject
  EntryCompressionPreprocessor(ModuleCompressionManager moduleCompressionManager) {
    this.moduleCompressionManager = moduleCompressionManager;
  }

  @Override
  public AppBundle preprocess(AppBundle bundle) {
    return bundle.toBuilder()
        .setRawModules(setEntryCompression(bundle.getModules().values(), bundle.getBundleConfig()))
        .build();
  }

  @CheckReturnValue
  private ImmutableList<BundleModule> setEntryCompression(
      ImmutableCollection<BundleModule> modules, BundleConfig bundleConfig) {
    return modules.stream()
        .map(module -> setEntryCompression(module, bundleConfig))
        .collect(toImmutableList());
  }

  private BundleModule setEntryCompression(BundleModule module, BundleConfig bundleConfig) {
    if (moduleCompressionManager.shouldForceUncompressAssets(
        bundleConfig, module.getAndroidManifest())) {
      return module.toBuilder()
          .setRawEntries(
              module.getEntries().stream()
                  .map(
                      entry ->
                          entry.toBuilder()
                              .setForceUncompressed(entry.getPath().startsWith(ASSETS_DIRECTORY))
                              .build())
                  .collect(toImmutableList()))
          .build();
    }
    return module;
  }
}
