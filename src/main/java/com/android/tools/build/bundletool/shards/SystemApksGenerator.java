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

package com.android.tools.build.bundletool.shards;

import static com.android.tools.build.bundletool.mergers.AndroidManifestMerger.manifestOverride;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.standaloneApkVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.mergers.ModuleSplitsToShardMerger;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.splitters.BinaryArtProfilesInjector;
import com.android.tools.build.bundletool.splitters.CodeTransparencyInjector;
import com.android.tools.build.bundletool.splitters.RuntimeEnabledSdkTableInjector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Generates system APKs for a device provided by device spec. */
public class SystemApksGenerator {

  private final ModuleSplitterForShards moduleSplitter;
  private final Sharder sharder;
  private final ModuleSplitsToShardMerger shardsMerger;
  private final Optional<DeviceSpec> deviceSpec;
  private final CodeTransparencyInjector codeTransparencyInjector;
  private final BinaryArtProfilesInjector binaryArtProfilesInjector;
  private final RuntimeEnabledSdkTableInjector runtimeEnabledSdkTableInjector;

  @Inject
  public SystemApksGenerator(
      ModuleSplitterForShards moduleSplitter,
      Sharder sharder,
      ModuleSplitsToShardMerger shardsMerger,
      Optional<DeviceSpec> deviceSpec,
      AppBundle appBundle) {
    this.moduleSplitter = moduleSplitter;
    this.sharder = sharder;
    this.shardsMerger = shardsMerger;
    this.deviceSpec = deviceSpec;
    this.codeTransparencyInjector = new CodeTransparencyInjector(appBundle);
    this.binaryArtProfilesInjector = new BinaryArtProfilesInjector(appBundle);
    this.runtimeEnabledSdkTableInjector = new RuntimeEnabledSdkTableInjector(appBundle);
  }

  /**
   * Generates sharded system APK and additional split APKs from the given modules. Additional
   * splits are available for languages that are not matched to a device spec and feature modules
   * that are not included into {@code modulesToFuse}.
   *
   * <p>We target the (ABI, Screen Density, Languages) configuration specified in the device spec.
   */
  public ImmutableList<ModuleSplit> generateSystemApks(
      ImmutableList<BundleModule> modules,
      ImmutableSet<BundleModuleName> modulesToFuse,
      ApkOptimizations apkOptimizations) {
    checkState(deviceSpec.isPresent(), "Device spec should be set when sharding for system apps.");
    ImmutableList<ModuleSplit> splits =
        modules.stream()
            .flatMap(
                module ->
                    moduleSplitter
                        .generateSplits(module, apkOptimizations.getSplitDimensions())
                        .stream())
            .collect(toImmutableList());

    ImmutableList<ModuleSplit> systemShard =
        Iterables.getOnlyElement(sharder.groupSplitsToShards(splits));

    return processSplitsOfSystemShard(systemShard, modulesToFuse).stream()
        .map(module -> applyUncompressedOptimizations(module, apkOptimizations))
        .map(codeTransparencyInjector::inject)
        .map(binaryArtProfilesInjector::inject)
        .map(runtimeEnabledSdkTableInjector::inject)
        .collect(toImmutableList());
  }

  private ImmutableList<ModuleSplit> processSplitsOfSystemShard(
      ImmutableList<ModuleSplit> splits, ImmutableSet<BundleModuleName> modulesToFuse) {
    ImmutableSet<ModuleSplit> splitsOfFusedModules =
        splits.stream()
            .filter(module -> modulesToFuse.contains(module.getModuleName()))
            .collect(toImmutableSet());

    ImmutableSet<ModuleSplit> splitsOfNonFusedModules =
        Sets.difference(ImmutableSet.copyOf(splits), splitsOfFusedModules).immutableCopy();

    ImmutableSet<ModuleSplit> splitsWithOnlyDeviceLanguages =
        filterOutUnusedLanguageSplits(splitsOfFusedModules);

    ModuleSplit systemSplit = mergeSplitsToSystemApk(splitsWithOnlyDeviceLanguages);
    AndroidManifest systemSplitManifest = systemSplit.getAndroidManifest();

    // Groups all the unmatched language splits for fused modules by language and fuse them to
    // generate a single split for each language.
    ImmutableSet<ModuleSplit> additionalLanguageSplits =
        Sets.difference(splitsOfFusedModules, splitsWithOnlyDeviceLanguages).stream()
            .collect(groupingByDeterministic(ModuleSplit::getApkTargeting))
            .values()
            .stream()
            .map(
                splitsPerLanguage ->
                    mergeLanguageSplitsIntoOne(splitsPerLanguage, systemSplitManifest))
            .collect(toImmutableSet());

    // Write split id and variant targeting for splits that are not fused.
    ImmutableList<ModuleSplit> additionalSplits =
        Sets.union(additionalLanguageSplits, splitsOfNonFusedModules).stream()
            .collect(groupingByDeterministic(ModuleSplit::getModuleName))
            .values()
            .stream()
            .flatMap(SystemApksGenerator::writeSplitIdInManifestHavingSameModule)
            .map(
                module ->
                    writeSplitTargetingAndSplitTypeToAdditionalSplit(
                        module, systemSplit.getVariantTargeting()))
            .collect(toImmutableList());

    return ImmutableList.<ModuleSplit>builder().add(systemSplit).addAll(additionalSplits).build();
  }

  private ImmutableSet<ModuleSplit> filterOutUnusedLanguageSplits(
      ImmutableSet<ModuleSplit> splits) {
    ApkMatcher apkMatcher = new ApkMatcher(deviceSpec.get());
    return splits.stream()
        .filter(
            split ->
                !split.getApkTargeting().hasLanguageTargeting()
                    || apkMatcher.matchesModuleSplitByTargeting(split))
        .collect(toImmutableSet());
  }

  private ModuleSplit mergeLanguageSplitsIntoOne(
      ImmutableList<ModuleSplit> languageSplits, AndroidManifest manifest) {
    return shardsMerger.mergeSingleShard(
        languageSplits, new HashMap<>(), SplitType.SPLIT, manifestOverride(manifest));
  }

  private ModuleSplit mergeSplitsToSystemApk(ImmutableSet<ModuleSplit> splits) {
    ModuleSplit merged = shardsMerger.mergeSingleShard(splits, Maps.newHashMap());
    return merged.toBuilder()
        .setVariantTargeting(standaloneApkVariantTargeting(merged))
        .setMasterSplit(true)
        .setSplitType(SplitType.SYSTEM)
        .build();
  }

  private static Stream<ModuleSplit> writeSplitIdInManifestHavingSameModule(
      ImmutableList<ModuleSplit> splits) {
    checkState(splits.stream().map(ModuleSplit::getModuleName).distinct().count() == 1);
    SuffixManager suffixManager = new SuffixManager();
    return splits.stream()
        .map(split -> split.writeSplitIdInManifest(suffixManager.createSuffix(split)));
  }

  private static ModuleSplit writeSplitTargetingAndSplitTypeToAdditionalSplit(
      ModuleSplit split, VariantTargeting variantTargeting) {
    return split.toBuilder()
        .setVariantTargeting(variantTargeting)
        .setSplitType(SplitType.SYSTEM)
        .build();
  }

  private static ModuleSplit applyUncompressedOptimizations(
      ModuleSplit split, ApkOptimizations optimizations) {
    if (!optimizations.getUncompressNativeLibraries() && !optimizations.getUncompressDexFiles()) {
      return split;
    }
    ImmutableList<ModuleEntry> entries =
        split.getEntries().stream()
            .map(entry -> applyUncompressedOptimizations(entry, optimizations))
            .collect(toImmutableList());
    return split.toBuilder()
        .setEntries(entries)
        .setAndroidManifest(
            split.isMasterSplit() && optimizations.getUncompressNativeLibraries()
                ? split.getAndroidManifest().toEditor().setExtractNativeLibsValue(false).save()
                : split.getAndroidManifest())
        .build();
  }

  private static ModuleEntry applyUncompressedOptimizations(
      ModuleEntry entry, ApkOptimizations optimizations) {
    if ((isNativeLibrary(entry) && optimizations.getUncompressNativeLibraries())
        || (isDex(entry) && optimizations.getUncompressDexFiles())) {
      return entry.toBuilder().setForceUncompressed(true).build();
    }
    return entry;
  }

  private static boolean isNativeLibrary(ModuleEntry entry) {
    ZipPath path = entry.getPath();
    return path.startsWith(BundleModule.LIB_DIRECTORY)
        && path.getFileName().toString().endsWith(".so");
  }

  private static boolean isDex(ModuleEntry entry) {
    return entry.getPath().startsWith(BundleModule.DEX_DIRECTORY);
  }
}
