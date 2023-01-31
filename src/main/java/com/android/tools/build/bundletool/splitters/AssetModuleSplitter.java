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
import static com.google.common.primitives.Ints.max;

import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Int32Value;

/** Splits an asset module into asset slices, each targeting a specific configuration. */
public class AssetModuleSplitter {
  private final BundleModule module;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final AppBundle appBundle;
  private final SuffixManager suffixManager = new SuffixManager();

  public AssetModuleSplitter(
      BundleModule module,
      ApkGenerationConfiguration apkGenerationConfiguration,
      AppBundle appBundle) {
    this.module = checkNotNull(module);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
    this.appBundle = checkNotNull(appBundle);
  }

  public ImmutableList<ModuleSplit> splitModule() {
    ImmutableList.Builder<ModuleSplit> splitsBuilder = ImmutableList.builder();

    // Assets splits.
    SplittingPipeline assetsPipeline = createAssetsSplittingPipeline();
    splitsBuilder.addAll(assetsPipeline.split(ModuleSplit.forModule(module)));

    ImmutableList<ModuleSplit> splits = splitsBuilder.build();
    if (module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
      int baseModuleMinSdk =
          apkGenerationConfiguration.getEnableBaseModuleMinSdkAsDefaultTargeting()
                  && appBundle.hasBaseModule()
              ? appBundle.getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion()
              : 1;
      int masterSplitMinSdk =
          splits.stream()
              .filter(ModuleSplit::isMasterSplit)
              .findFirst()
              .map(split -> split.getAndroidManifest().getEffectiveMinSdkVersion())
              .orElse(1);
      splits =
          splits.stream()
              .map(split -> addDefaultSdkApkTargeting(split, masterSplitMinSdk, baseModuleMinSdk))
              .collect(toImmutableList());
    }
    return splits.stream().map(this::setAssetSliceManifest).collect(toImmutableList());
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.TEXTURE_COMPRESSION_FORMAT)) {
      assetsSplitters.add(
          TextureCompressionFormatAssetsSplitter.create(
              apkGenerationConfiguration.shouldStripTargetingSuffix(
                  OptimizationDimension.TEXTURE_COMPRESSION_FORMAT)));
    }
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.DEVICE_TIER)) {
      assetsSplitters.add(
          DeviceTierAssetsSplitter.create(
              apkGenerationConfiguration.shouldStripTargetingSuffix(
                  OptimizationDimension.DEVICE_TIER)));
    }
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.COUNTRY_SET)) {
      assetsSplitters.add(
          CountrySetAssetsSplitter.create(
              apkGenerationConfiguration.shouldStripTargetingSuffix(
                  OptimizationDimension.COUNTRY_SET)));
    }
    return new SplittingPipeline(assetsSplitters.build());
  }

  private static ModuleSplit addDefaultSdkApkTargeting(
      ModuleSplit split, int masterSplitMinSdk, int baseModuleMinSdk) {
    if (split.getApkTargeting().hasSdkVersionTargeting()) {
      checkState(
          split.getApkTargeting().getSdkVersionTargeting().getValue(0).getMin().getValue()
              >= ANDROID_L_API_VERSION,
          "Module Split should target SDK versions above L.");
      return split;
    }

    int defaultSdkVersion = max(masterSplitMinSdk, baseModuleMinSdk, ANDROID_L_API_VERSION);
    return split.toBuilder()
        .setApkTargeting(
            split.getApkTargeting().toBuilder()
                .setSdkVersionTargeting(
                    SdkVersionTargeting.newBuilder()
                        .addValue(
                            SdkVersion.newBuilder()
                                .setMin(Int32Value.newBuilder().setValue(defaultSdkVersion))))
                .build())
        .build();
  }

  private ModuleSplit setAssetSliceManifest(ModuleSplit assetSlice) {
    String resolvedSuffix = suffixManager.createSuffix(assetSlice);
    return assetSlice.writeSplitIdInManifest(resolvedSuffix).setHasCodeInManifest(false);
  }
}
