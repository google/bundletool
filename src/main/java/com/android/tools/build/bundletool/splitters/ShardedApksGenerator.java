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

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.UNIVERSAL;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.model.BundleMetadata;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ShardedSystemSplits;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.SourceStamp.StampType;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Generates APKs sharded by ABI and screen density.
 *
 * <p>Supports generation of both standalone and system APKs.
 */
public final class ShardedApksGenerator {

  private final BundleConfig bundleConfig;
  private final BundleMetadata bundleMetadata;
  private final ApkBuildMode apkBuildMode;
  private final ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings;
  private final Optional<SourceStamp> stampSource;
  private final BundleSharder bundleSharder;

  @Inject
  public ShardedApksGenerator(
      BundleConfig bundleConfig,
      BundleMetadata bundleMetadata,
      ApkBuildMode apkBuildMode,
      ImmutableMap<OptimizationDimension, SuffixStripping> suffixStrippings,
      Optional<SourceStamp> stampSource,
      BundleSharder bundleSharder) {
    this.bundleConfig = bundleConfig;
    this.bundleMetadata = bundleMetadata;
    this.apkBuildMode = apkBuildMode;
    this.suffixStrippings = suffixStrippings;
    this.stampSource = stampSource;
    this.bundleSharder = bundleSharder;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules, ApkOptimizations apkOptimizations) {
    boolean strip64BitLibraries =
        bundleConfig.getOptimizations().getStandaloneConfig().getStrip64BitLibraries()
            && !apkBuildMode.equals(UNIVERSAL);
    BundleSharderConfiguration configuration =
        BundleSharderConfiguration.builder()
            .setStrip64BitLibrariesFromShards(strip64BitLibraries)
            .setSuffixStrippings(suffixStrippings)
            .build();

    ImmutableList<ModuleSplit> moduleSplits =
        ImmutableList.copyOf(
            setVariantTargetingAndSplitType(
                bundleSharder.shardBundle(
                    modules,
                    apkOptimizations.getStandaloneDimensions(),
                    bundleMetadata,
                    configuration),
                SplitType.STANDALONE));
    if (stampSource.isPresent()) {
      return moduleSplits.stream()
          .map(
              moduleSplit ->
                  moduleSplit.writeSourceStampInManifest(
                      stampSource.get().getSource(), StampType.STAMP_TYPE_STANDALONE_APK))
          .collect(toImmutableList());
    }
    return moduleSplits;
  }

  public ImmutableList<ModuleSplit> generateSystemSplits(
      ImmutableList<BundleModule> modules,
      ImmutableSet<BundleModuleName> modulesToFuse,
      ApkOptimizations apkOptimizations,
      Optional<DeviceSpec> deviceSpec) {
    BundleSharderConfiguration configuration =
        BundleSharderConfiguration.builder()
            .setStrip64BitLibrariesFromShards(
                bundleConfig.getOptimizations().getStandaloneConfig().getStrip64BitLibraries())
            .setSuffixStrippings(suffixStrippings)
            .setDeviceSpec(deviceSpec)
            .build();

    ShardedSystemSplits shardedApks =
        bundleSharder.shardForSystemApps(
            modules,
            modulesToFuse,
            apkOptimizations.getSplitDimensions(),
            bundleMetadata,
            configuration);

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
            .setStrip64BitLibrariesFromShards(
                bundleConfig.getOptimizations().getStandaloneConfig().getStrip64BitLibraries())
            .setSuffixStrippings(suffixStrippings)
            .build();

    ImmutableList<ModuleSplit> shardedApexApks =
        bundleSharder.shardApexBundle(getOnlyElement(modules), configuration);

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
