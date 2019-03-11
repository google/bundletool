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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ManifestEditor;
import com.android.tools.build.bundletool.model.ManifestMutator;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Int32Value;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Splits module into multiple parts called splits: each targets a specific configuration.
 *
 * <p>The splits ultimately become split APKs which are supported only since Android L onwards, so
 * the minSdkVersion is added to the targeting.
 */
public class ModuleSplitter {

  private final BundleModule module;
  private final ImmutableSet<String> allModuleNames;
  private final SuffixManager suffixManager = new SuffixManager();
  private final Version bundleVersion;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final VariantTargeting variantTargeting;

  private final AbiPlaceholderInjector abiPlaceholderInjector;

  @VisibleForTesting
  ModuleSplitter(BundleModule module, Version bundleVersion) {
    this(
        module,
        bundleVersion,
        ApkGenerationConfiguration.getDefaultInstance(),
        lPlusVariantTargeting(),
        /* allModuleNames= */ ImmutableSet.of());
  }

  public ModuleSplitter(
      BundleModule module,
      Version bundleVersion,
      ApkGenerationConfiguration apkGenerationConfiguration,
      VariantTargeting variantTargeting,
      ImmutableSet<String> allModuleNames) {
    this.module = checkNotNull(module);
    this.bundleVersion = checkNotNull(bundleVersion);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
    this.variantTargeting = checkNotNull(variantTargeting);
    this.abiPlaceholderInjector =
        new AbiPlaceholderInjector(apkGenerationConfiguration.getAbisForPlaceholderLibs());
    this.allModuleNames = allModuleNames;
  }

  public ImmutableList<ModuleSplit> splitModule() {
    if (apkGenerationConfiguration.isForInstantAppVariants()) {
      // Returns the list of module splits, ready for use as an instant app.
      return splitModuleInternal().stream()
          .map(this::makeInstantManifestChanges)
          .map(moduleSplit -> moduleSplit.toBuilder().setSplitType(SplitType.INSTANT).build())
          .collect(toImmutableList());
    } else {
      return splitModuleInternal().stream()
          .map(this::removeSplitName)
          .map(this::addPlaceHolderNativeLibsToBaseModule)
          .collect(toImmutableList());
    }
  }

  private ModuleSplit addPlaceHolderNativeLibsToBaseModule(ModuleSplit moduleSplit) {
    if (!apkGenerationConfiguration.getAbisForPlaceholderLibs().isEmpty()
        && moduleSplit.isBaseModuleSplit()
        && moduleSplit.isMasterSplit()) {
      return abiPlaceholderInjector.addPlaceholderNativeEntries(moduleSplit);
    } else {
      return moduleSplit;
    }
  }

  /** Common modifications to both the instant and installed splits. */
  private ImmutableList<ModuleSplit> splitModuleInternal() {
    return runSplitters().stream()
        .map(this::addLPlusApkTargeting)
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
    splits.addAll(resourcesPipeline.split(ModuleSplit.forResources(module, variantTargeting)));

    // Native libraries splits.
    SplittingPipeline nativePipeline = createNativeLibrariesSplittingPipeline();
    splits.addAll(nativePipeline.split(ModuleSplit.forNativeLibraries(module, variantTargeting)));

    // Assets splits.
    SplittingPipeline assetsPipeline = createAssetsSplittingPipeline();
    splits.addAll(assetsPipeline.split(ModuleSplit.forAssets(module, variantTargeting)));

    // Dex Files.
    SplittingPipeline dexPipeline = createDexSplittingPipeline();
    splits.addAll(dexPipeline.split(ModuleSplit.forDex(module, variantTargeting)));

    // Other files.
    splits.add(ModuleSplit.forRoot(module, variantTargeting));

    // Merging and making a master split.
    ImmutableList<ModuleSplit> mergedSplits =
        new SameTargetingMerger().merge(applyMasterManifestMutators(splits.build()));

    // Check that we have only one split with default targeting - the master split.
    ImmutableList<ModuleSplit> defaultTargetingSplits =
        mergedSplits.stream()
            .filter(split -> split.getApkTargeting().equals(ApkTargeting.getDefaultInstance()))
            .collect(toImmutableList());
    checkState(defaultTargetingSplits.size() == 1, "Expected one split with default targeting.");

    return mergedSplits;
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
   * Updates the split to insert instant app specific manifest changes:
   *
   * <ul>
   *   <li>Sets the targetSandboxVersion to 2.
   *   <li>Sets the minSdkVersion to 21 if it the minSdkVersion is lower than 21.
   *   <li>Removes any known splits from the base manifest, which may contain on demand split
   *       information.
   * </ul>
   */
  public ModuleSplit makeInstantManifestChanges(ModuleSplit moduleSplit) {
    AndroidManifest manifest = moduleSplit.getAndroidManifest();
    ManifestEditor editor = manifest.toEditor();
    editor.setTargetSandboxVersion(2);
    if (manifest.getEffectiveMinSdkVersion() < 21) {
      editor.setMinSdkVersion(21);
    }
    editor.removeUnknownSplitComponents(allModuleNames);
    return moduleSplit.toBuilder().setAndroidManifest(editor.save()).build();
  }

  private SplittingPipeline createResourcesSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> resourceSplitters = ImmutableList.builder();

    ImmutableSet<ResourceId> masterPinnedResources =
        apkGenerationConfiguration.getMasterPinnedResources();
    ImmutableSet<ResourceId> baseManifestReachableResources =
        apkGenerationConfiguration.getBaseManifestReachableResources();

    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.SCREEN_DENSITY)) {
      resourceSplitters.add(
          new ScreenDensityResourcesSplitter(
              bundleVersion,
              /* pinWholeResourceToMaster= */ masterPinnedResources::contains,
              /* pinLowestBucketOfResourceToMaster= */ baseManifestReachableResources::contains));
    }

    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.LANGUAGE)) {
      Predicate<ResourceTableEntry> pinLangResourceToMaster =
          Predicates.or(
              // Resources that are unconditionally in the master split.
              entry -> masterPinnedResources.contains(entry.getResourceId()),
              // Resources reachable from the AndroidManifest.xml should have at least one config
              // in the master split (ie. either the default config, or all configs).
              entry ->
                  baseManifestReachableResources.contains(entry.getResourceId())
                      && !hasDefaultConfig(entry));

      resourceSplitters.add(new LanguageResourcesSplitter(pinLangResourceToMaster));
    }

    return new SplittingPipeline(resourceSplitters.build());
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
    if (apkGenerationConfiguration.getEnableNativeLibraryCompressionSplitter()) {
      nativeSplitters.add(new NativeLibrariesCompressionSplitter(apkGenerationConfiguration));
    }
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.ABI)) {
      nativeSplitters.add(
          new AbiNativeLibrariesSplitter(apkGenerationConfiguration.getInclude64BitLibs()));
    }
    return new SplittingPipeline(nativeSplitters.build());
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.LANGUAGE)) {
      assetsSplitters.add(LanguageAssetsSplitter.create());
    }
    return new SplittingPipeline(assetsSplitters.build());
  }

  private SplittingPipeline createDexSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> dexSplitters = ImmutableList.builder();
    if (apkGenerationConfiguration.getEnableDexCompressionSplitter()) {
      dexSplitters.add(new DexCompressionSplitter());
    }

    return new SplittingPipeline(dexSplitters.build());
  }

  private static boolean targetsOnlyPreL(BundleModule module) {
    Optional<Integer> maxSdkVersion = module.getAndroidManifest().getMaxSdkVersion();
    return maxSdkVersion.isPresent() && maxSdkVersion.get() < ANDROID_L_API_VERSION;
  }

  /**
   * Adds L+ targeting to the Apk targeting of module split. If SDK targeting already exists, it's
   * not overridden but checked that it targets no L- devices.
   */
  private ModuleSplit addLPlusApkTargeting(ModuleSplit split) {
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

  private static boolean hasDefaultConfig(ResourceTableEntry entry) {
    return entry.getEntry().getConfigValueList().stream()
        .anyMatch(
            configValue -> configValue.getConfig().equals(Configuration.getDefaultInstance()));
  }
}
