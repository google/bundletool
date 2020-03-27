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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ShardedSystemSplits;
import com.android.tools.build.bundletool.model.SourceStamp.StampType;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Generates APKs sharded by ABI and screen density.
 *
 * <p>Supports generation of both standalone and system APKs.
 */
public final class ShardedApksGenerator {

  private final Path tempDir;
  private final Version bundleVersion;
  private final boolean strip64BitLibrariesFromShards;
  private final ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings;
  private final Optional<String> stampSource;

  public ShardedApksGenerator(
      Path tempDir, Version bundleVersion, boolean strip64BitLibrariesFromShards) {
    this(tempDir, bundleVersion, strip64BitLibrariesFromShards, ImmutableMap.of());
  }

  public ShardedApksGenerator(
      Path tempDir,
      Version bundleVersion,
      boolean strip64BitLibrariesFromShards,
      ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings) {
    this(
        tempDir,
        bundleVersion,
        strip64BitLibrariesFromShards,
        suffixStrippings,
        /* stampSource= */ Optional.empty());
  }

  public ShardedApksGenerator(
      Path tempDir,
      Version bundleVersion,
      boolean strip64BitLibrariesFromShards,
      ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings,
      Optional<String> stampSource) {
    this.tempDir = tempDir;
    this.bundleVersion = bundleVersion;
    this.strip64BitLibrariesFromShards = strip64BitLibrariesFromShards;
    this.suffixStrippings = suffixStrippings;
    this.stampSource = stampSource;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules,
      BundleMetadata bundleMetadata,
      ApkOptimizations apkOptimizations) {
    BundleSharderConfiguration configuration =
        BundleSharderConfiguration.builder()
            .setStrip64BitLibrariesFromShards(strip64BitLibrariesFromShards)
            .setSuffixStrippings(suffixStrippings)
            .build();

    BundleSharder bundleSharder = new BundleSharder(tempDir, bundleVersion, configuration);
    ImmutableList<ModuleSplit> moduleSplits =
        ImmutableList.copyOf(
            setVariantTargetingAndSplitType(
                bundleSharder.shardBundle(
                    modules, apkOptimizations.getStandaloneDimensions(), bundleMetadata),
                SplitType.STANDALONE));
    if (stampSource.isPresent()) {
      return moduleSplits.stream()
          .map(
              moduleSplit ->
                  moduleSplit.writeSourceStampInManifest(
                      stampSource.get(), StampType.STAMP_TYPE_STANDALONE_APK))
          .collect(toImmutableList());
    }
    return moduleSplits;
  }

  public ImmutableList<ModuleSplit> generateSystemSplits(
      ImmutableList<BundleModule> modules,
      ImmutableSet<BundleModuleName> modulesToFuse,
      BundleMetadata bundleMetadata,
      ApkOptimizations apkOptimizations,
      Optional<DeviceSpec> deviceSpec) {
    BundleSharderConfiguration configuration =
        BundleSharderConfiguration.builder()
            .setStrip64BitLibrariesFromShards(strip64BitLibrariesFromShards)
            .setSuffixStrippings(suffixStrippings)
            .setDeviceSpec(deviceSpec)
            .build();

    BundleSharder bundleSharder = new BundleSharder(tempDir, bundleVersion, configuration);
    ShardedSystemSplits shardedApks =
        bundleSharder.shardForSystemApps(
            modules, modulesToFuse, apkOptimizations.getSplitDimensions(), bundleMetadata);

    ModuleSplit fusedApk =
        setVariantTargetingAndSplitType(shardedApks.getSystemImageSplit(), SplitType.SYSTEM)
            .toBuilder()
            .setMasterSplit(true)
            .build();

    ImmutableList<ModuleSplit> additionalSplitApks =
        shardedApks.getAdditionalSplits().stream()
            .map(
                split ->
                    split.toBuilder()
                        .setVariantTargeting(fusedApk.getVariantTargeting())
                        .setSplitType(SplitType.SYSTEM)
                        .build())
            .collect(toImmutableList());

    return ImmutableList.<ModuleSplit>builder().add(fusedApk).addAll(additionalSplitApks).build();
  }

  public ImmutableList<ModuleSplit> generateApexSplits(ImmutableList<BundleModule> modules) {
    BundleSharderConfiguration configuration =
        BundleSharderConfiguration.builder()
            .setStrip64BitLibrariesFromShards(strip64BitLibrariesFromShards)
            .setSuffixStrippings(suffixStrippings)
            .build();

    BundleSharder bundleSharder = new BundleSharder(tempDir, bundleVersion, configuration);
    ImmutableList<ModuleSplit> shardedApexApks =
        bundleSharder.shardApexBundle(getOnlyElement(modules));

    return setVariantTargetingAndSplitType(shardedApexApks, SplitType.STANDALONE);
  }

  private static ImmutableList<ModuleSplit> setVariantTargetingAndSplitType(
      ImmutableList<ModuleSplit> standaloneApks, SplitType splitType) {
    return standaloneApks.stream()
        .map(moduleSplit -> setVariantTargetingAndSplitType(moduleSplit, splitType))
        .collect(toImmutableList());
  }

  private static ModuleSplit setVariantTargetingAndSplitType(
      ModuleSplit moduleSplit, SplitType splitType) {
    return moduleSplit.toBuilder()
        .setVariantTargeting(standaloneApkVariantTargeting(moduleSplit))
        .setSplitType(splitType)
        .build();
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
    if (apkTargeting.hasTextureCompressionFormatTargeting()) {
      variantTargeting.setTextureCompressionFormatTargeting(
          apkTargeting.getTextureCompressionFormatTargeting());
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
