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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableList;

/**
 * Generates asset slices from remote asset modules.
 *
 * <p>Each asset in the module is inserted in at most one asset slice, according to its target.
 */
public class AssetSlicesGenerator {

  private final ImmutableList<BundleModule> modules;
  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public AssetSlicesGenerator(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    this.modules = checkNotNull(modules);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
  }

  public ImmutableList<ModuleSplit> generateAssetSlices() {
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    for (BundleModule module : modules) {
      RemoteAssetModuleSplitter moduleSplitter =
          new RemoteAssetModuleSplitter(module, apkGenerationConfiguration);
      splits.addAll(moduleSplitter.splitModule());
    }
    return splits.build();
  }
}
