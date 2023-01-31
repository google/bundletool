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
package com.android.tools.build.bundletool.io;

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingBySortedKeys;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.ImmutableBiMap.toImmutableBiMap;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.mapping;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetModulesInfo;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.DefaultTargetingValue;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Commands.InstantMetadata;
import com.android.bundle.Commands.LocalTestingInfo;
import com.android.bundle.Commands.PermanentlyFusedModule;
import com.android.bundle.Commands.SdkVersionInformation;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Commands.VariantProperties;
import com.android.bundle.Config.AssetModulesConfig;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.commands.BuildApksModule.FirstVariantNumber;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.ApkModifier.ApkDescription.ApkType;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.Bundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.ManifestDeliveryElement;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Creates parts of table of contents and writes out APKs. */
public class ApkSerializerManager {
  private final Bundle bundle;
  private final ApkModifier apkModifier;

  private final int firstVariantNumber;
  private final ApkBuildMode apkBuildMode;

  private final ApkPathManager apkPathManager;
  private final ApkOptimizations apkOptimizations;
  private final ApkSerializer apkSerializer;

  @Inject
  public ApkSerializerManager(
      Bundle bundle,
      Optional<ApkModifier> apkModifier,
      @FirstVariantNumber Optional<Integer> firstVariantNumber,
      ApkBuildMode apkBuildMode,
      ApkPathManager apkPathManager,
      ApkOptimizations apkOptimizations,
      ApkSerializer apkSerializer) {
    this.bundle = bundle;
    this.apkModifier = apkModifier.orElse(ApkModifier.NO_OP);
    this.firstVariantNumber = firstVariantNumber.orElse(0);
    this.apkBuildMode = apkBuildMode;
    this.apkPathManager = apkPathManager;
    this.apkOptimizations = apkOptimizations;
    this.apkSerializer = apkSerializer;
  }

  /** Serialize App Bundle APKs. */
  public BuildApksResult serializeApkSet(
      ApkSetWriter apkSetWriter,
      GeneratedApks generatedApks,
      GeneratedAssetSlices generatedAssetSlices,
      Optional<DeviceSpec> deviceSpec,
      LocalTestingInfo localTestingInfo,
      ImmutableSet<BundleModuleName> permanentlyFusedModules) {
    try {
      BuildApksResult toc =
          serializeApkSetContent(
              apkSetWriter.getSplitsDirectory(),
              generatedApks,
              generatedAssetSlices,
              deviceSpec,
              localTestingInfo,
              permanentlyFusedModules);
      apkSetWriter.writeApkSet(toc);
      return toc;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Serialize App Bundle APKs without including TOC in the output archive. */
  public void serializeApkSetWithoutToc(
      ApkSetWriter apkSetWriter,
      GeneratedApks generatedApks,
      GeneratedAssetSlices generatedAssetSlices,
      Optional<DeviceSpec> deviceSpec,
      LocalTestingInfo localTestingInfo,
      ImmutableSet<BundleModuleName> permanentlyFusedModules) {
    try {
      BuildApksResult toc =
          serializeApkSetContent(
              apkSetWriter.getSplitsDirectory(),
              generatedApks,
              generatedAssetSlices,
              deviceSpec,
              localTestingInfo,
              permanentlyFusedModules);
      apkSetWriter.writeApkSetWithoutToc(toc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Serialize SDK Bundle APKs. */
  public void serializeSdkApkSet(ApkSetWriter apkSetWriter, GeneratedApks generatedApks) {
    try {
      BuildSdkApksResult toc =
          serializeSdkApkSetContent(apkSetWriter.getSplitsDirectory(), generatedApks);
      apkSetWriter.writeApkSet(toc);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private BuildApksResult serializeApkSetContent(
      Path outputDirectory,
      GeneratedApks generatedApks,
      GeneratedAssetSlices generatedAssetSlices,
      Optional<DeviceSpec> deviceSpec,
      LocalTestingInfo localTestingInfo,
      ImmutableSet<BundleModuleName> permanentlyFusedModules) {
    ImmutableList<Variant> allVariantsWithTargeting =
        serializeApks(outputDirectory, generatedApks, deviceSpec);
    ImmutableList<AssetSliceSet> allAssetSliceSets =
        serializeAssetSlices(outputDirectory, generatedAssetSlices, deviceSpec);
    // Finalize the output archive.
    BuildApksResult.Builder apksResult =
        BuildApksResult.newBuilder()
            .setPackageName(bundle.getPackageName())
            .addAllVariant(allVariantsWithTargeting)
            .setBundletool(
                Bundletool.newBuilder()
                    .setVersion(BundleToolVersion.getCurrentVersion().toString()))
            .addAllAssetSliceSet(allAssetSliceSets)
            .setLocalTestingInfo(localTestingInfo);
    BundleConfig bundleConfig = ((AppBundle) bundle).getBundleConfig();
    if (bundleConfig.hasAssetModulesConfig()) {
      apksResult.setAssetModulesInfo(getAssetModulesInfo(bundleConfig.getAssetModulesConfig()));
    }
    apksResult.addAllDefaultTargetingValue(getDefaultTargetingValues(bundleConfig));
    permanentlyFusedModules.forEach(
        moduleName ->
            apksResult.addPermanentlyFusedModules(
                PermanentlyFusedModule.newBuilder().setName(moduleName.getName())));
    return apksResult.build();
  }

  private BuildSdkApksResult serializeSdkApkSetContent(
      Path outputDirectory, GeneratedApks generatedApks) {
    ImmutableList<Variant> allVariantsWithTargeting =
        serializeApks(outputDirectory, generatedApks, /* deviceSpec= */ Optional.empty());
    SdkBundle sdkBundle = (SdkBundle) bundle;
    checkState(sdkBundle.getVersionCode().isPresent(), "Missing version code for SDK Bundle.");
    return BuildSdkApksResult.newBuilder()
        .setPackageName(sdkBundle.getPackageName())
        .addAllVariant(allVariantsWithTargeting)
        .setBundletool(
            Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
        .setVersion(
            SdkVersionInformation.newBuilder()
                .setVersionCode(sdkBundle.getVersionCode().get())
                .setMajor(sdkBundle.getMajorVersion())
                .setMinor(sdkBundle.getMinorVersion())
                .setPatch(sdkBundle.getPatchVersion())
                .build())
        .build();
  }

  @VisibleForTesting
  ImmutableList<Variant> serializeApks(
      Path outputDirectory, GeneratedApks generatedApks, Optional<DeviceSpec> deviceSpec) {
    validateInput(generatedApks, apkBuildMode);

    // Running with system APK mode generates a fused APK and additional unmatched language splits.
    // To avoid filtering of unmatched language splits we skip device filtering for system mode.
    Predicate<ModuleSplit> deviceFilter =
        deviceSpec.isPresent() && !apkBuildMode.equals(SYSTEM)
            ? new ApkMatcher(
                    addDefaultCountrySetIfNecessary(
                        addDefaultDeviceTierIfNecessary(deviceSpec.get())))
                ::matchesModuleSplitByTargeting
            : alwaysTrue();

    ImmutableListMultimap<VariantKey, ModuleSplit> splitsByVariant =
        generatedApks.getAllApksGroupedByOrderedVariants();

    // Assign the variant numbers to each variant present.
    AtomicInteger variantNumberCounter = new AtomicInteger(firstVariantNumber);
    ImmutableMap<VariantKey, Integer> variantNumberByVariantKey =
        splitsByVariant.keySet().stream()
            .collect(toImmutableMap(identity(), unused -> variantNumberCounter.getAndIncrement()));

    // 1. Remove APKs not matching the device spec.
    // 2. Modify the APKs based on the ApkModifier.
    // 3. Serialize all APKs in parallel.

    // Modifies the APK using APK modifier, then returns a map by extracting the variant
    // of APK first and later clearing out its variant targeting.
    ImmutableListMultimap<VariantKey, ModuleSplit> finalSplitsByVariant =
        splitsByVariant.entries().stream()
            .filter(keyModuleSplitEntry -> deviceFilter.test(keyModuleSplitEntry.getValue()))
            .collect(
                groupingBySortedKeys(
                    Entry::getKey,
                    entry ->
                        clearVariantTargeting(
                            modifyApk(
                                entry.getValue(), variantNumberByVariantKey.get(entry.getKey())))));

    // After variant targeting of APKs are cleared, there might be duplicate APKs
    // which are removed and the distinct APKs are then serialized in parallel.
    ImmutableBiMap<ZipPath, ModuleSplit> splitsByRelativePath =
        finalSplitsByVariant.values().stream()
            .distinct()
            .collect(toImmutableBiMap(apkPathManager::getApkPath, identity()));

    ImmutableMap<ZipPath, ApkDescription> apkDescriptionsByRelativePath =
        apkSerializer.serialize(outputDirectory, splitsByRelativePath);

    // Build the result proto.
    ImmutableList.Builder<Variant> variants = ImmutableList.builder();
    for (VariantKey variantKey : finalSplitsByVariant.keySet()) {
      Variant.Builder variant =
          Variant.newBuilder()
              .setVariantNumber(variantNumberByVariantKey.get(variantKey))
              .setTargeting(variantKey.getVariantTargeting())
              .setVariantProperties(getVariantProperties(finalSplitsByVariant.get(variantKey)));

      Multimap<BundleModuleName, ModuleSplit> splitsByModuleName =
          finalSplitsByVariant.get(variantKey).stream()
              .collect(groupingBySortedKeys(ModuleSplit::getModuleName));

      for (BundleModuleName moduleName : splitsByModuleName.keySet()) {
        variant.addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(
                    bundle
                        .getModule(moduleName)
                        .getModuleMetadata(
                            variant
                                .getTargeting()
                                .getSdkRuntimeTargeting()
                                .getRequiresSdkRuntime()))
                .addAllApkDescription(
                    splitsByModuleName.get(moduleName).stream()
                        .map(split -> splitsByRelativePath.inverse().get(split))
                        .map(apkDescriptionsByRelativePath::get)
                        .collect(toImmutableList())));
      }
      variants.add(variant.build());
    }

    return variants.build();
  }

  @VisibleForTesting
  ImmutableList<AssetSliceSet> serializeAssetSlices(
      Path outputDirectory,
      GeneratedAssetSlices generatedAssetSlices,
      Optional<DeviceSpec> deviceSpec) {

    Predicate<ModuleSplit> deviceFilter =
        deviceSpec.isPresent()
            ? new ApkMatcher(
                    addDefaultCountrySetIfNecessary(
                        addDefaultDeviceTierIfNecessary(deviceSpec.get())))
                ::matchesModuleSplitByTargeting
            : alwaysTrue();

    ImmutableMap<ZipPath, ModuleSplit> assetSplitsByRelativePath =
        generatedAssetSlices.getAssetSlices().stream()
            .filter(deviceFilter)
            .collect(toImmutableMap(apkPathManager::getApkPath, identity()));

    ImmutableMap<ZipPath, ApkDescription> apkDescriptionsByRelativePath =
        apkSerializer.serialize(outputDirectory, assetSplitsByRelativePath);

    ImmutableMap<BundleModuleName, ImmutableList<ApkDescription>> serializedApksByModuleName =
        assetSplitsByRelativePath.keySet().stream()
            .collect(
                groupingByDeterministic(
                    relativePath -> assetSplitsByRelativePath.get(relativePath).getModuleName(),
                    mapping(apkDescriptionsByRelativePath::get, toImmutableList())));

    return serializedApksByModuleName.entrySet().stream()
        .map(
            entry ->
                AssetSliceSet.newBuilder()
                    .setAssetModuleMetadata(
                        getAssetModuleMetadata(bundle.getModule(entry.getKey())))
                    .addAllApkDescription(entry.getValue())
                    .build())
        .collect(toImmutableList());
  }

  private VariantProperties getVariantProperties(ImmutableList<ModuleSplit> modules) {
    ImmutableList<ModuleEntry> nativeLibEntries =
        modules.stream()
            .filter(module -> module.getNativeConfig().isPresent())
            .flatMap(
                module ->
                    module.getNativeConfig().get().getDirectoryList().stream()
                        .flatMap(dir -> module.findEntriesUnderPath(dir.getPath())))
            .collect(toImmutableList());
    ImmutableList<ModuleEntry> dexEntries =
        modules.stream()
            .flatMap(module -> module.getEntries().stream())
            .filter(entry -> entry.getPath().startsWith(DEX_DIRECTORY))
            .collect(toImmutableList());
    return VariantProperties.newBuilder()
        .setUncompressedDex(
            !dexEntries.isEmpty()
                && dexEntries.stream().allMatch(ModuleEntry::getForceUncompressed))
        .setUncompressedNativeLibraries(
            !nativeLibEntries.isEmpty()
                && nativeLibEntries.stream().allMatch(ModuleEntry::getForceUncompressed))
        .setSparseEncoding(modules.stream().allMatch(ModuleSplit::getSparseEncoding))
        .build();
  }

  private AssetModuleMetadata getAssetModuleMetadata(BundleModule module) {
    AndroidManifest manifest = module.getAndroidManifest();
    AssetModuleMetadata.Builder metadataBuilder =
        AssetModuleMetadata.newBuilder().setName(module.getName().getName());
    Optional<ManifestDeliveryElement> persistentDelivery = manifest.getManifestDeliveryElement();
    metadataBuilder.setDeliveryType(
        persistentDelivery
            .map(delivery -> getDeliveryType(delivery))
            .orElse(DeliveryType.INSTALL_TIME));
    // The module is instant if either the dist:instant attribute is true or the
    // dist:instant-delivery element is present.
    boolean isInstantModule = module.isInstantModule();
    InstantMetadata.Builder instantMetadataBuilder =
        InstantMetadata.newBuilder().setIsInstant(isInstantModule);
    // The ManifestDeliveryElement is present if the dist:instant-delivery element is used.
    Optional<ManifestDeliveryElement> instantDelivery =
        manifest.getInstantManifestDeliveryElement();
    if (isInstantModule) {
      // If it's an instant-enabled module, the instant delivery is on-demand if the dist:instant
      // attribute was set to true or if the dist:instant-delivery element was used without an
      // install-time element.
      instantMetadataBuilder.setDeliveryType(
          instantDelivery
              .map(delivery -> getDeliveryType(delivery))
              .orElse(DeliveryType.ON_DEMAND));
    }
    metadataBuilder.setInstantMetadata(instantMetadataBuilder.build());
    return metadataBuilder.build();
  }

  private static void validateInput(GeneratedApks generatedApks, ApkBuildMode apkBuildMode) {
    switch (apkBuildMode) {
      case DEFAULT:
        checkArgument(
            generatedApks.getSystemApks().isEmpty(),
            "Internal error: System APKs can only be set in system mode.");
        break;
      case UNIVERSAL:
        checkArgument(
            generatedApks.getSplitApks().isEmpty()
                && generatedApks.getInstantApks().isEmpty()
                && generatedApks.getArchivedApks().isEmpty()
                && generatedApks.getSystemApks().isEmpty(),
            "Internal error: For universal APK expecting only standalone APKs.");
        break;
      case SYSTEM:
        checkArgument(
            generatedApks.getSplitApks().isEmpty()
                && generatedApks.getInstantApks().isEmpty()
                && generatedApks.getArchivedApks().isEmpty()
                && generatedApks.getStandaloneApks().isEmpty(),
            "Internal error: For system mode expecting only system APKs.");
        break;
      case PERSISTENT:
        checkArgument(
            generatedApks.getSystemApks().isEmpty(),
            "Internal error: System APKs not expected with persistent mode.");
        checkArgument(
            generatedApks.getInstantApks().isEmpty(),
            "Internal error: Instant APKs not expected with persistent mode.");
        break;
      case INSTANT:
        checkArgument(
            generatedApks.getSystemApks().isEmpty(),
            "Internal error: System APKs not expected with instant mode.");
        checkArgument(
            generatedApks.getSplitApks().isEmpty() && generatedApks.getStandaloneApks().isEmpty(),
            "Internal error: Persistent APKs not expected with instant mode.");
        break;
      case ARCHIVE:
        checkArgument(
            generatedApks.getSplitApks().isEmpty()
                && generatedApks.getInstantApks().isEmpty()
                && generatedApks.getStandaloneApks().isEmpty()
                && generatedApks.getSystemApks().isEmpty(),
            "Internal error: For archive mode expecting only archived APKs.");
        break;
    }
  }

  private ModuleSplit modifyApk(ModuleSplit moduleSplit, int variantNumber) {
    ApkModifier.ApkDescription apkDescription =
        ApkModifier.ApkDescription.builder()
            .setBase(moduleSplit.isBaseModuleSplit())
            .setApkType(
                moduleSplit.getSplitType().equals(SplitType.STANDALONE)
                    ? ApkType.STANDALONE
                    : (moduleSplit.isMasterSplit() ? ApkType.MASTER_SPLIT : ApkType.CONFIG_SPLIT))
            .setVariantNumber(variantNumber)
            .setVariantTargeting(moduleSplit.getVariantTargeting())
            .setApkTargeting(moduleSplit.getApkTargeting())
            .build();

    return moduleSplit.toBuilder()
        .setAndroidManifest(
            apkModifier.modifyManifest(moduleSplit.getAndroidManifest(), apkDescription))
        .build();
  }

  private static ModuleSplit clearVariantTargeting(ModuleSplit moduleSplit) {
    return moduleSplit.toBuilder()
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .build();
  }

  private static AssetModulesInfo getAssetModulesInfo(AssetModulesConfig assetModulesConfig) {
    return AssetModulesInfo.newBuilder()
        .addAllAppVersion(assetModulesConfig.getAppVersionList())
        .setAssetVersionTag(assetModulesConfig.getAssetVersionTag())
        .build();
  }

  private static ImmutableList<DefaultTargetingValue> getDefaultTargetingValues(
      BundleConfig bundleConfig) {
    return bundleConfig.getOptimizations().getSplitsConfig().getSplitDimensionList().stream()
        .filter(SplitDimension::hasSuffixStripping)
        .map(
            splitDimension ->
                DefaultTargetingValue.newBuilder()
                    .setDimension(splitDimension.getValue())
                    .setDefaultValue(splitDimension.getSuffixStripping().getDefaultSuffix())
                    .build())
        .collect(toImmutableList());
  }

  private static DeliveryType getDeliveryType(ManifestDeliveryElement deliveryElement) {
    if (deliveryElement.hasOnDemandElement()) {
      return DeliveryType.ON_DEMAND;
    }
    if (deliveryElement.hasFastFollowElement()) {
      return DeliveryType.FAST_FOLLOW;
    }
    return DeliveryType.INSTALL_TIME;
  }

  /**
   * Adds a default device tier to the given {@link DeviceSpec} if it has none.
   *
   * <p>The default tier is taken from the optimization settings in the {@link
   * com.android.bundle.Config.BundleConfig}. If suffix stripping is enabled but the default tier is
   * unspecified, it defaults to 0.
   */
  private DeviceSpec addDefaultDeviceTierIfNecessary(DeviceSpec deviceSpec) {
    if (deviceSpec.hasDeviceTier()) {
      return deviceSpec;
    }
    Optional<SuffixStripping> deviceTierSuffix =
        Optional.ofNullable(
            apkOptimizations.getSuffixStrippings().get(OptimizationDimension.DEVICE_TIER));
    if (!deviceTierSuffix.isPresent()) {
      return deviceSpec;
    }
    return deviceSpec.toBuilder()
        .setDeviceTier(
            Int32Value.of(
                deviceTierSuffix
                    .map(
                        suffix ->
                            // Use the standard default value 0 if the app doesn't specify an
                            // explicit default.
                            suffix.getDefaultSuffix().isEmpty()
                                ? 0
                                : Integer.parseInt(suffix.getDefaultSuffix()))
                    .orElse(0)))
        .build();
  }

  /**
   * Adds a default country set to the given {@link DeviceSpec} if it has none.
   *
   * <p>The default country set is taken from the optimization settings in the {@link
   * com.android.bundle.Config.BundleConfig}.
   */
  private DeviceSpec addDefaultCountrySetIfNecessary(DeviceSpec deviceSpec) {
    if (deviceSpec.hasCountrySet()) {
      return deviceSpec;
    }
    Optional<SuffixStripping> countrySetSuffix =
        Optional.ofNullable(
            apkOptimizations.getSuffixStrippings().get(OptimizationDimension.COUNTRY_SET));
    if (!countrySetSuffix.isPresent()) {
      return deviceSpec;
    }
    return deviceSpec.toBuilder()
        .setCountrySet(
            StringValue.of(countrySetSuffix.map(SuffixStripping::getDefaultSuffix).orElse("")))
        .build();
  }
}
