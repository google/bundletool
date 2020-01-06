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

import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Int32Value;

/** Splits an asset module into asset slices, each targeting a specific configuration. */
public class AssetModuleSplitter {
  private final BundleModule module;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final SuffixManager suffixManager = new SuffixManager();

  public AssetModuleSplitter(
      BundleModule module, ApkGenerationConfiguration apkGenerationConfiguration) {
    this.module = checkNotNull(module);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
  }

  public ImmutableList<ModuleSplit> splitModule() {
    ImmutableList.Builder<ModuleSplit> splitsBuilder = ImmutableList.builder();

    // Assets splits.
    SplittingPipeline assetsPipeline = createAssetsSplittingPipeline();
    splitsBuilder.addAll(assetsPipeline.split(ModuleSplit.forModule(module)));

    ImmutableList<ModuleSplit> splits = splitsBuilder.build();
    if (module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
      splits =
          splits.stream().map(AssetModuleSplitter::addLPlusApkTargeting).collect(toImmutableList());
    }
    return splits.stream().map(this::setAssetSliceManifest).collect(toImmutableList());
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    return new SplittingPipeline(assetsSplitters.build());
  }

  private static ModuleSplit addLPlusApkTargeting(ModuleSplit split) {
    if (split.getApkTargeting().hasSdkVersionTargeting()) {
      checkState(
          split.getApkTargeting().getSdkVersionTargeting().getValue(0).getMin().getValue()
              >= ANDROID_L_API_VERSION,
          "Module Split should target SDK versions above L.");
      return split;
    }

    return split.toBuilder()
        .setApkTargeting(
            split.getApkTargeting().toBuilder()
                .setSdkVersionTargeting(
                    SdkVersionTargeting.newBuilder()
                        .addValue(
                            SdkVersion.newBuilder()
                                .setMin(Int32Value.newBuilder().setValue(ANDROID_L_API_VERSION))))
                .build())
        .build();
  }

  private ModuleSplit setAssetSliceManifest(ModuleSplit assetSlice) {
    String resolvedSuffix = suffixManager.createSuffix(assetSlice);
    return assetSlice.writeSplitIdInManifest(resolvedSuffix);
  }
}
