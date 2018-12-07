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

import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Generates APKs sharded by ABI and screen density.
 *
 * <p>Supports generation of both standalone and system APKs.
 */
public final class ShardedApksGenerator {

  private final Path tempDir;
  private final Version bundleVersion;
  private final SplitType splitType;
  private final boolean generate64BitShards;

  public ShardedApksGenerator(Path tempDir, Version bundleVersion) {
    this(tempDir, bundleVersion, SplitType.STANDALONE, /* generate64BitShards= */ true);
  }

  public ShardedApksGenerator(
      Path tempDir, Version bundleVersion, SplitType splitType, boolean generate64BitShards) {
    this.tempDir = tempDir;
    this.bundleVersion = bundleVersion;
    this.splitType = splitType;
    this.generate64BitShards = generate64BitShards;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules,
      BundleMetadata bundleMetadata,
      ApkOptimizations apkOptimizations) {

    BundleSharder bundleSharder = new BundleSharder(tempDir, bundleVersion, generate64BitShards);
    ImmutableList<ModuleSplit> shardedApks =
        bundleSharder.shardBundle(modules, apkOptimizations.getSplitDimensions(), bundleMetadata);

    return setVariantTargetingAndSplitType(shardedApks, splitType);
  }

  public ImmutableList<ModuleSplit> generateApexSplits(ImmutableList<BundleModule> modules) {

    BundleSharder bundleSharder = new BundleSharder(tempDir, bundleVersion, generate64BitShards);
    ImmutableList<ModuleSplit> shardedApexApks =
        bundleSharder.shardApexBundle(getOnlyElement(modules));

    return setVariantTargetingAndSplitType(shardedApexApks, splitType);
  }

  private static ImmutableList<ModuleSplit> setVariantTargetingAndSplitType(
      ImmutableList<ModuleSplit> standaloneApks, SplitType splitType) {
    return standaloneApks.stream()
        .map(
            moduleSplit ->
                moduleSplit
                    .toBuilder()
                    .setVariantTargeting(standaloneApkVariantTargeting(moduleSplit))
                    .setSplitType(splitType)
                    .build())
        .collect(toImmutableList());
  }

  private static VariantTargeting standaloneApkVariantTargeting(ModuleSplit standaloneApk) {
    ApkTargeting apkTargeting = standaloneApk.getApkTargeting();

    VariantTargeting.Builder variantTargeting = VariantTargeting.newBuilder();
    if (apkTargeting.hasAbiTargeting()) {
      variantTargeting.setAbiTargeting(apkTargeting.getAbiTargeting());
    }
    if (apkTargeting.hasScreenDensityTargeting()) {
      variantTargeting.setScreenDensityTargeting(apkTargeting.getScreenDensityTargeting());
    }
    if (apkTargeting.hasMultiAbiTargeting()) {
      variantTargeting.setMultiAbiTargeting(apkTargeting.getMultiAbiTargeting());
    }
    variantTargeting.setSdkVersionTargeting(sdkVersionTargeting(standaloneApk));

    return variantTargeting.build();
  }

  private static SdkVersionTargeting sdkVersionTargeting(ModuleSplit moduleSplit) {
    return SdkVersionTargeting.newBuilder()
        .addValue(sdkVersionFrom(moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion()))
        .build();
  }
}
