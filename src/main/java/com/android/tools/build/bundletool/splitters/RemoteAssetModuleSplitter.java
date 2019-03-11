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
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.google.common.collect.ImmutableList;

/** Splits a remote asset module into asset slices, each targeting a specific configuration. */
public class RemoteAssetModuleSplitter {
  private final BundleModule module;
  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public RemoteAssetModuleSplitter(
      BundleModule module, ApkGenerationConfiguration apkGenerationConfiguration) {
    this.module = checkNotNull(module);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
  }

  public ImmutableList<ModuleSplit> splitModule() {
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    // Assets splits.
    SplittingPipeline assetsPipeline = createAssetsSplittingPipeline();
    splits.addAll(assetsPipeline.split(ModuleSplit.fromAssetBundleModule(module)));

    return splits.build();
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    return new SplittingPipeline(assetsSplitters.build());
  }
}
