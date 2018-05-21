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

import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Int32Value;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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


  public ModuleSplitter(
      BundleModule module,
      ImmutableSet<OptimizationDimension> optimizationDimensions,
      Version bundleVersion) {
    this.optimizationDimensions = optimizationDimensions;
    this.module = module;
    this.bundleVersion = bundleVersion;
  }


  public ImmutableList<ModuleSplit> splitModule() {
    return splitModuleInternal()
        .stream()
        .map(this::addLPlusTargeting)
        .map(this::writeSplitIdInManifest)
        .collect(toImmutableList());
  }

  private ImmutableList<ModuleSplit> splitModuleInternal() {
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

    // Other files.
    splits.add(ModuleSplit.forCode(module));
    splits.add(ModuleSplit.forRoot(module));

    // Merging and making a master split.
    ImmutableList<ModuleSplit> mergedSplits = new SameTargetingMerger().merge(splits.build());

    // Check that we have only one split with default targeting - the master split.
    ImmutableList<ModuleSplit> defaultTargetingSplits =
        mergedSplits
            .stream()
            .filter(split -> split.getApkTargeting().equals(ApkTargeting.getDefaultInstance()))
            .collect(toImmutableList());
    checkState(defaultTargetingSplits.size() == 1, "Expected one split with default targeting.");

    return mergedSplits;
  }

  /* Writes the final manifest that reflects the Split ID. */
  public ModuleSplit writeSplitIdInManifest(ModuleSplit moduleSplit) {
    String resolvedSuffix = suffixManager.resolveSuffix(moduleSplit.getSuffix());
    return moduleSplit.writeSplitIdInManifest(resolvedSuffix);
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

  private SplittingPipeline createNativeLibrariesSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> nativeSplitters = ImmutableList.builder();
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

  private ModuleSplit addLPlusTargeting(ModuleSplit split) {
    checkState(
        !split.getApkTargeting().hasSdkVersionTargeting(), "Split already targets SDK version.");

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
    private final Set<String> usedSuffixes = new HashSet<>();

    synchronized String resolveSuffix(String proposedSuffix) {
      String currentProposal = proposedSuffix;
      int serialNumber = 1;
      while (usedSuffixes.contains(currentProposal)) {
        serialNumber++;
        currentProposal = String.format("%s_%d", proposedSuffix, serialNumber);
      }
      usedSuffixes.add(currentProposal);
      return currentProposal;
    }
  }
}
