/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.build.bundletool.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ManifestMutator;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.protobuf.Int32Value;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.concurrent.GuardedBy;

/**
 * Splits module into multiple parts called splits: each targets a specific configuration.
 *
 * <p>The splits ultimately become split APKs which are supported only since Android L onwards, so
 * the minSdkVersion is added to the targeting.
 */
public class ModuleSplitter {

  private final BundleModule module;
  private final ImmutableSet<OptimizationDimension> optimizationDimensions;
  private final SuffixManager suffixManager = new SuffixManager();
  private final Version bundleVersion;


  private boolean enableNativeLibraryCompressionSplitter = false;

  public ModuleSplitter(
      BundleModule module,
      ImmutableSet<OptimizationDimension> optimizationDimensions,
      Version bundleVersion) {
    this.optimizationDimensions = optimizationDimensions;
    this.module = module;
    this.bundleVersion = bundleVersion;
  }


  public void setEnableNativeLibraryCompressionSplitter(boolean value) {
    this.enableNativeLibraryCompressionSplitter = value;
  }

  public ImmutableList<ModuleSplit> splitModule() {
    return splitModuleInternal()
        .stream()
        .map(this::removeSplitName)
        .collect(toImmutableList());
  }

  /** Returns the list of module splits, ready for use as an instant app. */
  public ImmutableList<ModuleSplit> splitInstantModule() {
    return splitModuleInternal()
        .stream()
        .map(ModuleSplitter::writeTargetSandboxVersion)
        .map(moduleSplit -> moduleSplit.toBuilder().setSplitType(SplitType.INSTANT).build())
        .collect(toImmutableList());
  }

  /** Common modifications to both the instant and installed splits. */
  private ImmutableList<ModuleSplit> splitModuleInternal() {
    return runSplitters()
        .stream()
        .map(this::addLPlusTargeting)
        .map(this::writeSplitIdInManifest)
        .collect(toImmutableList());
  }

  private ImmutableList<ModuleSplit> runSplitters() {
    if (targetsOnlyPreL(module)) {
      throw CommandExecutionException.builder()
          .withMessage(
              "Cannot split module '%s' because it does not target devices on Android L or above.",
              module.getName())
          .build();
    }

    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    // Resources splits.
    SplittingPipeline resourcesPipeline = createResourcesSplittingPipeline();
    splits.addAll(resourcesPipeline.split(ModuleSplit.forResources(module)));

    // Native libraries splits.
    SplittingPipeline nativePipeline = createNativeLibrariesSplittingPipeline();
    splits.addAll(nativePipeline.split(ModuleSplit.forNativeLibraries(module)));

    // Assets splits.
    SplittingPipeline assetsPipeline = createAssetsSplittingPipeline();
    splits.addAll(assetsPipeline.split(ModuleSplit.forAssets(module)));

    ImmutableList<ModuleSplit> dexSplits = ImmutableList.of(ModuleSplit.forDex(module));
    splits.addAll(dexSplits);

    // Other files.
    splits.add(ModuleSplit.forRoot(module));

    ImmutableMultimap<VariantTargeting, ModuleSplit> variantModuleSplitMap =
        new VariantGrouper().groupByVariant(splits.build());

    ImmutableList.Builder<ModuleSplit> mergedSplits = ImmutableList.builder();
    for (Collection<ModuleSplit> moduleSplits : variantModuleSplitMap.asMap().values()) {
      // Merging and making a master split from single variant.
      mergedSplits.addAll(
          new SameTargetingMerger()
              .merge(applyMasterManifestMutators(ImmutableList.copyOf(moduleSplits))));
    }

    return mergedSplits.build();
  }

  /* Writes the final manifest that reflects the Split ID. */
  public ModuleSplit writeSplitIdInManifest(ModuleSplit moduleSplit) {
    String resolvedSuffix =
        suffixManager.createSuffix(moduleSplit);
    return moduleSplit.writeSplitIdInManifest(resolvedSuffix);
  }

  /* Removes the {@code splitName} attribute, if it exists in the manifest. */
  public ModuleSplit removeSplitName(ModuleSplit moduleSplit) {

    return moduleSplit.removeSplitName();
  }

  /**
   * Updates the split to insert the targetSandboxVersion as 2, which is required for instant apps.
   */
  public static ModuleSplit writeTargetSandboxVersion(ModuleSplit moduleSplit) {
    AndroidManifest apkManifest =
        moduleSplit.getAndroidManifest().toEditor().setTargetSandboxVersion(2).save();
    return moduleSplit.toBuilder().setAndroidManifest(apkManifest).build();
  }

  private SplittingPipeline createResourcesSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> resourceSplitters = ImmutableList.builder();
    if (optimizationDimensions.contains(OptimizationDimension.SCREEN_DENSITY)) {
      resourceSplitters.add(new ScreenDensityResourcesSplitter(bundleVersion));
    }
    if (optimizationDimensions.contains(OptimizationDimension.LANGUAGE)) {
      resourceSplitters.add(new LanguageResourcesSplitter());
    }
    return SplittingPipeline.create(resourceSplitters.build());
  }

  /**
   * Updates the android manifest of the master splits, using manifest mutators across all splits.
   */
  public static ImmutableList<ModuleSplit> applyMasterManifestMutators(
      ImmutableCollection<ModuleSplit> moduleSplits) {
    checkState(
        moduleSplits.stream().map(ModuleSplit::getVariantTargeting).distinct().count() == 1,
        "Expected same variant targeting across all splits.");
    ImmutableList<ManifestMutator> manifestMutators =
        moduleSplits
            .stream()
            .flatMap(moduleSplit -> moduleSplit.getMasterManifestMutators().stream())
            .collect(toImmutableList());

    return moduleSplits
        .stream()
        .map(
            moduleSplit -> {
              if (moduleSplit.isMasterSplit()) {
                moduleSplit =
                    moduleSplit
                        .toBuilder()
                        .setAndroidManifest(
                            moduleSplit.getAndroidManifest().applyMutators(manifestMutators))
                        .build();
              }
              return moduleSplit;
            })
        .collect(toImmutableList());
  }

  private SplittingPipeline createNativeLibrariesSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> nativeSplitters = ImmutableList.builder();
    if (enableNativeLibraryCompressionSplitter) {
      nativeSplitters.add(new NativeLibrariesCompressionSplitter());
    }
    if (optimizationDimensions.contains(OptimizationDimension.ABI)) {
      nativeSplitters.add(new AbiNativeLibrariesSplitter());
    }
    return SplittingPipeline.create(nativeSplitters.build());
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    if (optimizationDimensions.contains(OptimizationDimension.LANGUAGE)) {
      assetsSplitters.add(LanguageAssetsSplitter.create());
    }
    return SplittingPipeline.create(assetsSplitters.build());
  }


  private static boolean targetsOnlyPreL(BundleModule module) {
    Optional<Integer> maxSdkVersion = module.getAndroidManifest().getMaxSdkVersion();
    return maxSdkVersion.isPresent() && maxSdkVersion.get() < ANDROID_L_API_VERSION;
  }

  /**
   * Adds L+ targeting to the module split. If SDK targeting already exists, it's not overridden but
   * checked that it targets no L- devices.
   */
  private ModuleSplit addLPlusTargeting(ModuleSplit split) {
    if (split.getApkTargeting().hasSdkVersionTargeting()) {
      checkState(
          split.getApkTargeting().getSdkVersionTargeting().getValue(0).getMin().getValue()
              >= ANDROID_L_API_VERSION,
          "Module Split should target SDK versions above L.");
      return split;
    }

    return split
        .toBuilder()
        .setApkTargeting(
            split
                .getApkTargeting()
                .toBuilder()
                .setSdkVersionTargeting(
                    SdkVersionTargeting.newBuilder()
                        .addValue(
                            SdkVersion.newBuilder()
                                .setMin(Int32Value.newBuilder().setValue(ANDROID_L_API_VERSION))))
                .build())
        .build();
  }

  private static class SuffixManager {
    @GuardedBy("this")
    private final Multimap<VariantTargeting, String> usedSuffixes = HashMultimap.create();

    synchronized String createSuffix(ModuleSplit moduleSplit) {
      String currentProposal = moduleSplit.getSuffix();
      int serialNumber = 1;
      while (usedSuffixes.containsEntry(moduleSplit.getVariantTargeting(), currentProposal)) {
        serialNumber++;
        currentProposal = String.format("%s_%d", moduleSplit.getSuffix(), serialNumber);
      }
      usedSuffixes.put(moduleSplit.getVariantTargeting(), currentProposal);
      return currentProposal;
    }
  }
}
