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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getMinSdk;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_V2_API_VERSION;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.PIN_LOWEST_DENSITY_OF_EACH_STYLE_TO_MASTER;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.primitives.Ints.max;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.mergers.SameTargetingMerger;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ManifestEditor;
import com.android.tools.build.bundletool.model.ManifestMutator;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.SourceStampConstants.StampType;
import com.android.tools.build.bundletool.model.SuffixManager;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.protobuf.Int32Value;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Splits module into multiple parts called splits: each targets a specific configuration.
 *
 * <p>The splits ultimately become split APKs which are supported only since Android L onwards, so
 * the minSdkVersion is added to the targeting.
 *
 * <p>Note: This is for split apks, not for asset slices.
 */
public class ModuleSplitter {

  private final BundleModule module;
  private final ImmutableSet<String> allModuleNames;
  private final SuffixManager suffixManager = new SuffixManager();
  private final Version bundleVersion;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final VariantTargeting variantTargeting;
  private final Optional<String> stampSource;
  private final StampType stampType;
  private final AppBundle appBundle;

  private final AbiPlaceholderInjector abiPlaceholderInjector;
  private final PinSpecInjector pinSpecInjector;
  private final CodeTransparencyInjector codeTransparencyInjector;
  private final BinaryArtProfilesInjector binaryArtProfilesInjector;
  private final RuntimeEnabledSdkTableInjector runtimeEnabledSdkTableInjector;

  @VisibleForTesting
  public static ModuleSplitter createForTest(
      BundleModule module, AppBundle appBundle, Version bundleVersion) {
    return new ModuleSplitter(
        module,
        bundleVersion,
        appBundle,
        ApkGenerationConfiguration.getDefaultInstance(),
        lPlusVariantTargeting(),
        /* allModuleNames= */ ImmutableSet.of(),
        /* stampSource= */ Optional.empty(),
        /* stampType= */ null);
  }

  public static ModuleSplitter createNoStamp(
      BundleModule module,
      Version bundleVersion,
      AppBundle appBundle,
      ApkGenerationConfiguration apkGenerationConfiguration,
      VariantTargeting variantTargeting,
      ImmutableSet<String> allModuleNames) {
    return new ModuleSplitter(
        module,
        bundleVersion,
        appBundle,
        apkGenerationConfiguration,
        variantTargeting,
        allModuleNames,
        /* stampSource= */ Optional.empty(),
        /* stampType= */ null);
  }

  public static ModuleSplitter create(
      BundleModule module,
      Version bundleVersion,
      AppBundle appBundle,
      ApkGenerationConfiguration apkGenerationConfiguration,
      VariantTargeting variantTargeting,
      ImmutableSet<String> allModuleNames,
      Optional<String> stampSource,
      StampType stampType) {
    return new ModuleSplitter(
        module,
        bundleVersion,
        appBundle,
        apkGenerationConfiguration,
        variantTargeting,
        allModuleNames,
        stampSource,
        stampType);
  }

  private ModuleSplitter(
      BundleModule module,
      Version bundleVersion,
      AppBundle appBundle,
      ApkGenerationConfiguration apkGenerationConfiguration,
      VariantTargeting variantTargeting,
      ImmutableSet<String> allModuleNames,
      Optional<String> stampSource,
      StampType stampType) {
    this.module = checkNotNull(module);
    this.bundleVersion = checkNotNull(bundleVersion);
    this.appBundle = appBundle;
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
    this.variantTargeting = checkNotNull(variantTargeting);
    this.abiPlaceholderInjector =
        new AbiPlaceholderInjector(apkGenerationConfiguration.getAbisForPlaceholderLibs());
    this.pinSpecInjector = new PinSpecInjector(module);
    this.codeTransparencyInjector = new CodeTransparencyInjector(appBundle);
    this.binaryArtProfilesInjector = new BinaryArtProfilesInjector(appBundle);
    this.runtimeEnabledSdkTableInjector = new RuntimeEnabledSdkTableInjector(appBundle);
    this.allModuleNames = allModuleNames;
    this.stampSource = stampSource;
    this.stampType = stampType;
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
          .map(this::addUsesSdkLibraryTagsToMainSplitOfBaseModule)
          .map(this::sanitizeManifestEntriesRequiredByPrivacySandboxSdk)
          .map(this::overrideMinSdkVersionOfSdkRuntimeVariant)
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

  private ModuleSplit addUsesSdkLibraryTagsToMainSplitOfBaseModule(ModuleSplit moduleSplit) {
    if (!appBundle.getRuntimeEnabledSdkDependencies().isEmpty()
        && variantTargeting.getSdkRuntimeTargeting().getRequiresSdkRuntime()
        && moduleSplit.isBaseModuleSplit()
        && moduleSplit.isMasterSplit()) {
      return moduleSplit.addUsesSdkLibraryElements(
          appBundle.getRuntimeEnabledSdkDependencies().values());
    }
    return moduleSplit;
  }

  private ModuleSplit overrideMinSdkVersionOfSdkRuntimeVariant(ModuleSplit moduleSplit) {
    if (variantTargeting.getSdkRuntimeTargeting().getRequiresSdkRuntime()
        && moduleSplit.isMasterSplit()) {
      return moduleSplit.overrideMinSdkVersionForSdkSandbox();
    }
    return moduleSplit;
  }

  /**
   * If the variant is SdkRuntime variant, this method deletes elements that have {@link
   * AndroidManifest#REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME} attribute with value {@code
   * true} from the manifest of the main split of the base module.
   *
   * <p>For any variant, this method also deletes {@link
   * AndroidManifest#REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME} attributes from the manifest of
   * the main split of the base module.
   *
   * <p>This method is no-op if split is not the main split of the base module.
   */
  private ModuleSplit sanitizeManifestEntriesRequiredByPrivacySandboxSdk(ModuleSplit moduleSplit) {
    if (appBundle.getRuntimeEnabledSdkDependencies().isEmpty()
        || !moduleSplit.isBaseModuleSplit()
        || !moduleSplit.isMasterSplit()) {
      return moduleSplit;
    }
    if (variantTargeting.getSdkRuntimeTargeting().getRequiresSdkRuntime()) {
      return removeRequiredByPrivacySandboxSdkAttributes(
          removeElementsRequiredByPrivacySandboxSdk(moduleSplit));
    }
    return removeRequiredByPrivacySandboxSdkAttributes(moduleSplit);
  }

  private ModuleSplit removeElementsRequiredByPrivacySandboxSdk(ModuleSplit moduleSplit) {
    AndroidManifest manifest = moduleSplit.getAndroidManifest();
    ManifestEditor editor = manifest.toEditor();
    editor.removeElementsRequiredByPrivacySandboxSdk();
    return moduleSplit.toBuilder().setAndroidManifest(editor.save()).build();
  }

  private ModuleSplit removeRequiredByPrivacySandboxSdkAttributes(ModuleSplit moduleSplit) {
    AndroidManifest manifest = moduleSplit.getAndroidManifest();
    ManifestEditor editor = manifest.toEditor();
    editor.removeRequiredByPrivacySandboxSdkAttributes();
    return moduleSplit.toBuilder().setAndroidManifest(editor.save()).build();
  }

  /** Common modifications to both the instant and installed splits. */
  private ImmutableList<ModuleSplit> splitModuleInternal() {
    ImmutableList<ModuleSplit> moduleSplits = runSplitters();
    int baseModuleMinSdk =
        apkGenerationConfiguration.getEnableBaseModuleMinSdkAsDefaultTargeting()
                && appBundle.hasBaseModule()
            ? appBundle.getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion()
            : 1;
    int masterSplitMinSdk =
        moduleSplits.stream()
            .filter(ModuleSplit::isMasterSplit)
            .findFirst()
            .map(moduleSplit -> moduleSplit.getAndroidManifest().getEffectiveMinSdkVersion())
            .orElse(1);
    moduleSplits =
        moduleSplits.stream()
            .map(pinSpecInjector::inject)
            .map(codeTransparencyInjector::inject)
            .map(binaryArtProfilesInjector::inject)
            .map(runtimeEnabledSdkTableInjector::inject)
            .map(this::addApkTargetingForSigningConfiguration)
            .map(
                moduleSplit ->
                    addDefaultSdkApkTargeting(moduleSplit, masterSplitMinSdk, baseModuleMinSdk))
            .map(this::writeSplitIdInManifest)
            .map(ModuleSplit::addApplicationElementIfMissingInManifest)
            .collect(toImmutableList());
    if (stampSource.isPresent()) {
      return moduleSplits.stream()
          .map(moduleSplit -> moduleSplit.writeSourceStampInManifest(stampSource.get(), stampType))
          .collect(toImmutableList());
    }
    return moduleSplits;
  }

  private ImmutableList<ModuleSplit> runSplitters() {
    if (targetsOnlyPreL(module)) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
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

    if (apkGenerationConfiguration.getEnableSparseEncodingVariant()) {
      ImmutableList.Builder<ModuleSplit> splitsWithSparse = ImmutableList.builder();
      splitsWithSparse.addAll(
          splits.build().stream().map(this::applySparseEncoding).collect(toImmutableList()));
      splits = splitsWithSparse;
    }

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

  private ModuleSplit applySparseEncoding(ModuleSplit split) {
    int variantSdkTargeting =
        Iterables.getOnlyElement(
                split.getVariantTargeting().getSdkVersionTargeting().getValueList())
            .getMin()
            .getValue();

    return variantSdkTargeting < ANDROID_S_V2_API_VERSION
        ? split
        : split.toBuilder().setSparseEncoding(true).build();
  }

  /* Writes the final manifest that reflects the Split ID. */
  public ModuleSplit writeSplitIdInManifest(ModuleSplit moduleSplit) {
    String resolvedSuffix = suffixManager.createSuffix(moduleSplit);
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

    ImmutableSet<ResourceId> masterPinnedResourceIds =
        apkGenerationConfiguration.getMasterPinnedResourceIds();
    ImmutableSet<String> masterPinnedResourceNames =
        apkGenerationConfiguration.getMasterPinnedResourceNames();
    ImmutableSet<ResourceId> baseManifestReachableResources =
        apkGenerationConfiguration.getBaseManifestReachableResources();

    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.SCREEN_DENSITY)) {
      resourceSplitters.add(
          new ScreenDensityResourcesSplitter(
              bundleVersion,
              /* pinWholeResourceToMaster= */ masterPinnedResourceIds::contains,
              /* pinLowestBucketOfResourceToMaster= */ baseManifestReachableResources::contains,
              PIN_LOWEST_DENSITY_OF_EACH_STYLE_TO_MASTER.enabledForVersion(bundleVersion)));
    }

    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.LANGUAGE)) {
      Predicate<ResourceTableEntry> pinLangResourceToMaster =
          Predicates.or(
              // Resources that are unconditionally in the master split.
              entry -> masterPinnedResourceIds.contains(entry.getResourceId()),
              entry -> masterPinnedResourceNames.contains(entry.getEntry().getName()),
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
        moduleSplits.stream()
            .flatMap(moduleSplit -> moduleSplit.getMasterManifestMutators().stream())
            .collect(toImmutableList());

    return moduleSplits.stream()
        .map(
            moduleSplit -> {
              if (moduleSplit.isMasterSplit()) {
                moduleSplit =
                    moduleSplit.toBuilder()
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
    nativeSplitters.add(new NativeLibrariesCompressionSplitter(apkGenerationConfiguration));
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.ABI)) {
      nativeSplitters.add(new AbiNativeLibrariesSplitter());
    }
    nativeSplitters.add(new SanitizerNativeLibrariesSplitter());
    return new SplittingPipeline(nativeSplitters.build());
  }

  private SplittingPipeline createAssetsSplittingPipeline() {
    ImmutableList.Builder<ModuleSplitSplitter> assetsSplitters = ImmutableList.builder();
    if (apkGenerationConfiguration
        .getOptimizationDimensions()
        .contains(OptimizationDimension.LANGUAGE)) {
      assetsSplitters.add(LanguageAssetsSplitter.create());
    }
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
   * Adds default SDK targeting to the Apk targeting of module split. If SDK targeting already
   * exists, it's not overridden but checked that it targets no L- devices.
   */
  private ModuleSplit addDefaultSdkApkTargeting(
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

  /**
   * Adds R+ targeting to the {@link ApkTargeting} of a module split if (a) the configuration is set
   * to restrict v3 signing to R+, and (b) the variant is targeted at R+. If SDK targeting already
   * exists and is greater than R+, then it is not overridden.
   */
  private ModuleSplit addApkTargetingForSigningConfiguration(ModuleSplit split) {
    if (!apkGenerationConfiguration.getMinSdkForAdditionalVariantWithV3Rotation().isPresent()) {
      return split;
    }
    int minimumV3RotationApiVersion =
        apkGenerationConfiguration.getMinSdkForAdditionalVariantWithV3Rotation().get();
    if (getMinSdk(variantTargeting.getSdkVersionTargeting()) >= minimumV3RotationApiVersion
        && getMinSdk(split.getApkTargeting().getSdkVersionTargeting())
            < minimumV3RotationApiVersion) {
      return split.toBuilder()
          .setApkTargeting(
              split.getApkTargeting().toBuilder()
                  .setSdkVersionTargeting(
                      SdkVersionTargeting.newBuilder()
                          .addValue(sdkVersionFrom(minimumV3RotationApiVersion)))
                  .build())
          .build();
    }
    return split;
  }

  private static boolean hasDefaultConfig(ResourceTableEntry entry) {
    return entry.getEntry().getConfigValueList().stream()
        .anyMatch(
            configValue -> configValue.getConfig().equals(Configuration.getDefaultInstance()));
  }
}
