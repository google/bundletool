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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.ARCHIVE;
import static com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode.SYSTEM;
import static com.android.tools.build.bundletool.commands.ExtractApksCommand.ALL_MODULES_SHORTCUT;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.RESOURCES_REFERENCED_IN_MANIFEST_TO_MASTER_SPLIT;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.LocalTestingInfo;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.ResourceOptimizations.SparseEncoding;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.archive.ArchivedApksGenerator;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.commands.BuildApksCommand.SystemApkOption;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.ModuleMatcher;
import com.android.tools.build.bundletool.io.ApkSerializerManager;
import com.android.tools.build.bundletool.io.ApkSetWriter;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.mergers.BundleModuleMerger;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.exceptions.IncompatibleDeviceException;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.model.utils.LocaleConfigXmlInjector;
import com.android.tools.build.bundletool.model.utils.ModuleDependenciesUtils;
import com.android.tools.build.bundletool.model.utils.SplitsXmlInjector;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.model.version.VersionGuardedFeature;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.preprocessors.LocalTestingPreprocessor;
import com.android.tools.build.bundletool.shards.ShardedApksFacade;
import com.android.tools.build.bundletool.splitters.ApkGenerationConfiguration;
import com.android.tools.build.bundletool.splitters.AssetSlicesGenerator;
import com.android.tools.build.bundletool.splitters.ResourceAnalyzer;
import com.android.tools.build.bundletool.splitters.SplitApksGenerator;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Executes the "build-apks" command. */
public final class BuildApksManager {

  private final AppBundle appBundle;
  private final BuildApksCommand command;
  private final Version bundletoolVersion;
  private final Optional<DeviceSpec> deviceSpec;
  private final TempDirectory tempDir;

  private final ApkSerializerManager apkSerializerManager;
  private final SplitApksGenerator splitApksGenerator;
  private final ShardedApksFacade shardedApksFacade;
  private final ApkOptimizations apkOptimizations;
  private final ArchivedApksGenerator archivedApksGenerator;

  @Inject
  BuildApksManager(
      AppBundle appBundle,
      BuildApksCommand command,
      Version bundletoolVersion,
      Optional<DeviceSpec> deviceSpec,
      TempDirectory tempDir,
      ApkSerializerManager apkSerializerManager,
      SplitApksGenerator splitApksGenerator,
      ShardedApksFacade shardedApksFacade,
      ApkOptimizations apkOptimizations,
      ArchivedApksGenerator archivedApksGenerator) {
    this.appBundle = appBundle;
    this.command = command;
    this.bundletoolVersion = bundletoolVersion;
    this.deviceSpec = deviceSpec;
    this.tempDir = tempDir;
    this.splitApksGenerator = splitApksGenerator;
    this.apkSerializerManager = apkSerializerManager;
    this.shardedApksFacade = shardedApksFacade;
    this.apkOptimizations = apkOptimizations;
    this.archivedApksGenerator = archivedApksGenerator;
  }

  public void execute() throws IOException {
    ImmutableSet<BundleModuleName> permanentlyFusedModules = ImmutableSet.of();
    ImmutableSet<BundleModule> requestedModules =
        command.getModules().isEmpty()
            ? ImmutableSet.of()
            : ModuleDependenciesUtils.getModulesIncludingDependencies(
                appBundle, getBundleModules(appBundle, command.getModules()));

    GeneratedApks.Builder generatedApksBuilder = GeneratedApks.builder();
    GeneratedAssetSlices.Builder generatedAssetSlices = GeneratedAssetSlices.builder();

    boolean enableUniversalAsFallbackForSplits = false;
    boolean enableInstallTimeNonRemovableModules = false;
    ApksToGenerate apksToGenerate =
        new ApksToGenerate(
            appBundle, command.getApkBuildMode(), enableUniversalAsFallbackForSplits, deviceSpec);

    // Split APKs
    if (apksToGenerate.generateSplitApks()) {
      AppBundle mergedAppBundle =
          BundleModuleMerger.mergeNonRemovableInstallTimeModules(
              appBundle, enableInstallTimeNonRemovableModules);
      AppBundleValidator bundleValidator = AppBundleValidator.create(command.getExtraValidators());
      bundleValidator.validate(mergedAppBundle);
      generatedApksBuilder.setSplitApks(generateSplitApks(mergedAppBundle));
      permanentlyFusedModules =
          Sets.difference(appBundle.getModules().keySet(), mergedAppBundle.getModules().keySet())
              .immutableCopy();
    }

    // Instant APKs
    if (apksToGenerate.generateInstantApks()) {
      generatedApksBuilder.setInstantApks(generateInstantApks(appBundle));
    }

    // Standalone APKs
    if (apksToGenerate.generateStandaloneApks()) {
      generatedApksBuilder.setStandaloneApks(generateStandaloneApks(appBundle));
    }

    // Universal APK
    if (apksToGenerate.generateUniversalApk()) {
      // Note: Universal APK is a special type of standalone, with no optimization dimensions.
      ImmutableList<BundleModule> modulesToFuse =
          requestedModules.isEmpty()
              ? modulesToFuse(getModulesForStandaloneApks(appBundle))
              : requestedModules.asList();
      generatedApksBuilder.setStandaloneApks(
          shardedApksFacade.generateSplits(
              modulesToFuse, ApkOptimizations.getOptimizationsForUniversalApk()));
    }

    // System APKs
    if (apksToGenerate.generateSystemApks()) {
      generatedApksBuilder.setSystemApks(generateSystemApks(appBundle, requestedModules));
    }

    // Archived APKs
    if (apksToGenerate.generateArchivedApks()) {
      generatedApksBuilder.setArchivedApks(generateArchivedApks(appBundle));
    }

    // Asset Slices
    if (apksToGenerate.generateAssetSlices()) {
      generatedAssetSlices.setAssetSlices(generateAssetSlices(appBundle));
    }

    // Populate alternative targeting based on variant targeting of all APKs.
    GeneratedApks generatedApks =
        AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
            generatedApksBuilder.build(),
            appBundle.isAssetOnly()
                ? Optional.empty()
                : appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion());

    // A variant is a set of APKs. One device is guaranteed to receive only APKs from the same. This
    // is why we are processing new entries like split.xml for each variant separately.
    generatedApks =
        GeneratedApks.fromModuleSplits(
            generatedApks.getAllApksGroupedByOrderedVariants().asMap().entrySet().stream()
                .map(
                    keySplit -> {
                      SplitsXmlInjector splitsXmlInjector = new SplitsXmlInjector();
                      ImmutableList<ModuleSplit> moduleSplits =
                          splitsXmlInjector.process(keySplit.getKey(), keySplit.getValue());
                      if (appBundle.injectLocaleConfig()) {
                        LocaleConfigXmlInjector localeConfigXmlInjector =
                            new LocaleConfigXmlInjector();
                        moduleSplits =
                            localeConfigXmlInjector.process(keySplit.getKey(), moduleSplits);
                      }
                      return moduleSplits;
                    })
                .flatMap(Collection::stream)
                .collect(toImmutableList()));

    if (deviceSpec.isPresent()) {
      // It is easier to fully check device compatibility once the splits have been generated (in
      // memory). Note that no costly I/O happened up until this point, so it's not too late for
      // this check.
      checkDeviceCompatibilityWithBundle(generatedApks, deviceSpec.get());
    }

    if (command.getOverwriteOutput() && Files.exists(command.getOutputFile())) {
      MoreFiles.deleteRecursively(command.getOutputFile(), RecursiveDeleteOption.ALLOW_INSECURE);
    }

    // Create variants and serialize APKs.
    apkSerializerManager.serializeApkSet(
        createApkSetWriter(tempDir.getPath()),
        generatedApks,
        generatedAssetSlices.build(),
        deviceSpec,
        getLocalTestingInfo(appBundle),
        permanentlyFusedModules);
  }

  private ImmutableList<ModuleSplit> generateStandaloneApks(AppBundle appBundle) {
    ImmutableList<BundleModule> allModules = getModulesForStandaloneApks(appBundle);
    return appBundle.isApex()
        ? shardedApksFacade.generateApexSplits(modulesToFuse(allModules))
        : shardedApksFacade.generateSplits(modulesToFuse(allModules), apkOptimizations);
  }

  private ImmutableList<ModuleSplit> generateAssetSlices(AppBundle appBundle) {
    ApkGenerationConfiguration assetSlicesGenerationConfiguration =
        getAssetSliceGenerationConfiguration();
    AssetSlicesGenerator assetSlicesGenerator =
        new AssetSlicesGenerator(
            appBundle,
            assetSlicesGenerationConfiguration,
            command.getAssetModulesVersionOverride());
    return assetSlicesGenerator.generateAssetSlices();
  }

  private ImmutableList<ModuleSplit> generateInstantApks(AppBundle appBundle) {
    ImmutableList<BundleModule> allFeatureModules = appBundle.getFeatureModules().values().asList();
    ImmutableList<BundleModule> instantModules =
        allFeatureModules.stream().filter(BundleModule::isInstantModule).collect(toImmutableList());
    ApkGenerationConfiguration instantApkGenerationConfiguration =
        getCommonSplitApkGenerationConfiguration(appBundle)
            .setForInstantAppVariants(true)
            // We can't enable this splitter for instant APKs, as currently they
            // only support one variant.
            .setEnableDexCompressionSplitter(false)
            .build();
    return splitApksGenerator.generateSplits(instantModules, instantApkGenerationConfiguration);
  }

  private ImmutableList<ModuleSplit> generateSplitApks(AppBundle appBundle) throws IOException {
    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        getCommonSplitApkGenerationConfiguration(appBundle);
    if (RESOURCES_REFERENCED_IN_MANIFEST_TO_MASTER_SPLIT.enabledForVersion(bundletoolVersion)) {
      // Make sure that resources reachable from the manifest of the base module will be
      // represented in the master split (by at least one config). This prevents the app
      // from crashing too soon (before reaching Application#onCreate), in case when only
      // the base master split is installed.
      apkGenerationConfiguration.setBaseManifestReachableResources(
          new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest());
    }

    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    return splitApksGenerator.generateSplits(featureModules, apkGenerationConfiguration.build());
  }

  private ImmutableList<ModuleSplit> generateSystemApks(
      AppBundle appBundle, ImmutableSet<BundleModule> requestedModules) {
    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    ImmutableList<BundleModule> modulesToFuse =
        requestedModules.isEmpty() ? modulesToFuse(featureModules) : requestedModules.asList();
    return shardedApksFacade.generateSystemSplits(
        /* modules= */ featureModules,
        /* modulesToFuse= */ modulesToFuse.stream()
            .map(BundleModule::getName)
            .collect(toImmutableSet()),
        getSystemApkOptimizations());
  }

  private ImmutableList<ModuleSplit> generateArchivedApks(AppBundle appBundle) throws IOException {
    return ImmutableList.of(
        archivedApksGenerator.generateArchivedApk(appBundle, command.getAppStorePackageName()));
  }

  private static void checkDeviceCompatibilityWithBundle(
      GeneratedApks generatedApks, DeviceSpec deviceSpec) {
    ApkMatcher apkMatcher = new ApkMatcher(deviceSpec);
    generatedApks.getAllApksStream().forEach(apkMatcher::checkCompatibleWithApkTargeting);
  }

  private ApkSetWriter createApkSetWriter(Path tempDir) {
    switch (command.getOutputFormat()) {
      case APK_SET:
        return ApkSetWriter.zip(tempDir, command.getOutputFile());
      case DIRECTORY:
        return ApkSetWriter.directory(command.getOutputFile());
    }
    throw InvalidCommandException.builder()
        .withInternalMessage("Unsupported output format '%s'.", command.getOutputFormat())
        .build();
  }

  private ApkGenerationConfiguration.Builder getCommonSplitApkGenerationConfiguration(
      AppBundle appBundle) {
    BundleConfig bundleConfig = appBundle.getBundleConfig();
    Version bundleToolVersion = Version.of(bundleConfig.getBundletool().getVersion());

    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setOptimizationDimensions(apkOptimizations.getSplitDimensions());

    apkGenerationConfiguration.setEnableUncompressedNativeLibraries(
        apkOptimizations.getUncompressNativeLibraries());
    apkGenerationConfiguration.setEnableDexCompressionSplitter(
        apkOptimizations.getUncompressDexFiles());
    apkGenerationConfiguration.setDexCompressionSplitterForTargetSdk(
        apkOptimizations.getUncompressedDexTargetSdk());
    apkGenerationConfiguration.setEnableSparseEncodingVariant(
        bundleConfig
            .getOptimizations()
            .getResourceOptimizations()
            .getSparseEncoding()
            .equals(SparseEncoding.VARIANT_FOR_SDK_32));

    apkGenerationConfiguration.setInstallableOnExternalStorage(
        appBundle
            .getBaseModule()
            .getAndroidManifest()
            .getInstallLocationValue()
            .map(
                installLocation ->
                    installLocation.equals("auto") || installLocation.equals("preferExternal"))
            .orElse(false));

    apkGenerationConfiguration.setMasterPinnedResourceIds(appBundle.getMasterPinnedResourceIds());

    apkGenerationConfiguration.setMasterPinnedResourceNames(
        appBundle.getMasterPinnedResourceNames());

    apkGenerationConfiguration.setSuffixStrippings(apkOptimizations.getSuffixStrippings());

    apkGenerationConfiguration.setEnableBaseModuleMinSdkAsDefaultTargeting(
        command.getEnableBaseModuleMinSdkAsDefaultTargeting());

    command
        .getMinSdkForAdditionalVariantWithV3Rotation()
        .ifPresent(apkGenerationConfiguration::setMinSdkForAdditionalVariantWithV3Rotation);

    return apkGenerationConfiguration;
  }

  private ApkGenerationConfiguration getAssetSliceGenerationConfiguration() {
    return ApkGenerationConfiguration.builder()
        .setEnableBaseModuleMinSdkAsDefaultTargeting(
            command.getEnableBaseModuleMinSdkAsDefaultTargeting())
        .setOptimizationDimensions(apkOptimizations.getSplitDimensionsForAssetModules())
        .setSuffixStrippings(apkOptimizations.getSuffixStrippings())
        .build();
  }

  private ImmutableList<BundleModule> modulesToFuse(ImmutableList<BundleModule> modules) {
    return modules.stream()
        .filter(BundleModule::isIncludedInFusing)
        .filter(
            module -> !command.getFuseOnlyDeviceMatchingModules() || matchModuleToDevice(module))
        .collect(toImmutableList());
  }

  private boolean matchModuleToDevice(BundleModule module) {
    if (!this.deviceSpec.isPresent()) {
      return false;
    }
    return new ModuleMatcher(this.deviceSpec.get())
        .matchesModuleTargeting(module.getModuleMetadata().getTargeting());
  }

  private ApkOptimizations getSystemApkOptimizations() {
    ImmutableSet<SystemApkOption> systemApkOptions = command.getSystemApkOptions();
    return apkOptimizations.toBuilder()
        .setUncompressNativeLibraries(
            systemApkOptions.contains(SystemApkOption.UNCOMPRESSED_NATIVE_LIBRARIES))
        .setUncompressDexFiles(systemApkOptions.contains(SystemApkOption.UNCOMPRESSED_DEX_FILES))
        .build();
  }

  private static boolean targetsOnlyPreL(AppBundle bundle) {
    Optional<Integer> maxSdkVersion =
        bundle.getBaseModule().getAndroidManifest().getMaxSdkVersion();
    return maxSdkVersion.isPresent() && maxSdkVersion.get() < Versions.ANDROID_L_API_VERSION;
  }

  private static boolean targetsPreL(AppBundle bundle) {
    int baseMinSdkVersion = bundle.getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion();
    return baseMinSdkVersion < Versions.ANDROID_L_API_VERSION;
  }

  private static ImmutableList<BundleModule> getBundleModules(
      AppBundle appBundle, ImmutableSet<String> moduleNames) {
    if (moduleNames.contains(ALL_MODULES_SHORTCUT)) {
      return appBundle.getModules().values().asList();
    }
    return moduleNames.stream()
        .map(BundleModuleName::create)
        .map(appBundle::getModule)
        .collect(toImmutableList());
  }

  private static ImmutableList<BundleModule> getModulesForStandaloneApks(AppBundle appBundle) {
    return Stream.concat(
            appBundle.getFeatureModules().values().stream(),
            appBundle.getAssetModules().values().stream()
                .filter(
                    module ->
                        module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)))
        .collect(toImmutableList());
  }

  private static LocalTestingInfo getLocalTestingInfo(AppBundle bundle) {
    LocalTestingInfo.Builder localTestingInfo = LocalTestingInfo.newBuilder();
    if (bundle.isAssetOnly()) {
      // Local testing is not supported for asset-only bundles.
      return localTestingInfo.setEnabled(false).build();
    }
    bundle
        .getBaseModule()
        .getAndroidManifest()
        .getMetadataValue(LocalTestingPreprocessor.METADATA_NAME)
        .ifPresent(
            localTestingPath ->
                localTestingInfo.setEnabled(true).setLocalTestingPath(localTestingPath));
    return localTestingInfo.build();
  }

  private static class ApksToGenerate {
    private final AppBundle appBundle;
    private final ApkBuildMode apkBuildMode;
    private final boolean enableUniversalAsFallbackForSplits;
    private final Optional<DeviceSpec> deviceSpec;

    private ApksToGenerate(
        AppBundle appBundle,
        ApkBuildMode apkBuildMode,
        boolean enableUniversalAsFallbackForSplits,
        Optional<DeviceSpec> deviceSpec) {
      this.appBundle = appBundle;
      this.apkBuildMode = apkBuildMode;
      this.enableUniversalAsFallbackForSplits = enableUniversalAsFallbackForSplits;
      this.deviceSpec = deviceSpec;
      validate();
    }

    private void validate() {
      if (appBundle.isApex() && apkBuildMode.equals(ApkBuildMode.UNIVERSAL)) {
        throw InvalidCommandException.builder()
            .withInternalMessage("APEX bundles do not support universal apks.")
            .build();
      }

      if (deviceSpec.isPresent()) {
        int deviceSdk = deviceSpec.get().getSdkVersion();
        Optional<Integer> appMaxSdk =
            appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion();

        if ((apkBuildMode.equals(ApkBuildMode.DEFAULT)
            || apkBuildMode.equals(ApkBuildMode.PERSISTENT))) {
          if (deviceSdk >= Versions.ANDROID_L_API_VERSION) {
            if (!generateSplitApks()) {
              throw IncompatibleDeviceException.builder()
                  .withUserMessage(
                      "App Bundle targets pre-L devices, but the device has SDK version higher "
                          + "or equal to L.")
                  .build();
            }
          } else {
            if (!generateStandaloneApks()) {
              throw IncompatibleDeviceException.builder()
                  .withUserMessage(
                      "App Bundle targets L+ devices, but the device has SDK version lower than L.")
                  .build();
            }
          }
        }

        if (appMaxSdk.isPresent() && deviceSdk > appMaxSdk.get()) {
          throw IncompatibleDeviceException.builder()
              .withUserMessage(
                  "Max SDK version of the App Bundle is lower than SDK version of the device")
              .build();
        }
      }

      boolean generatesAtLeastOneApk =
          generateStandaloneApks()
              || generateSplitApks()
              || generateInstantApks()
              || generateUniversalApk()
              || generateSystemApks()
              || generateArchivedApks()
              || generateAssetSlices();
      if (!generatesAtLeastOneApk) {
        throw InvalidCommandException.builder().withInternalMessage("No APKs to generate.").build();
      }
    }

    public boolean generateSplitApks() {
      if (appBundle.isApex()) {
        return false;
      }
      if (appBundle.isAssetOnly()) {
        return false;
      }
      if (!apkBuildMode.equals(ApkBuildMode.DEFAULT)
          && !apkBuildMode.equals(ApkBuildMode.PERSISTENT)) {
        return false;
      }
      return !targetsOnlyPreL(appBundle)
          && deviceSpec
              .map(spec -> spec.getSdkVersion() >= Versions.ANDROID_L_API_VERSION)
              .orElse(true);
    }

    public boolean generateStandaloneApks() {
      if (!apkBuildMode.equals(ApkBuildMode.DEFAULT)
          && !apkBuildMode.equals(ApkBuildMode.PERSISTENT)) {
        return false;
      }
      if (appBundle.isAssetOnly()) {
        return false;
      }
      if (appBundle.isApex()) {
        return true;
      }
      return targetsPreL(appBundle)
          && deviceSpec
              .map(spec -> spec.getSdkVersion() < Versions.ANDROID_L_API_VERSION)
              .orElse(true);
    }

    public boolean generateInstantApks() {
      if (appBundle.isApex()) {
        return false;
      }
      if (appBundle.isAssetOnly()) {
        return false;
      }
      return apkBuildMode.equals(ApkBuildMode.DEFAULT) || apkBuildMode.equals(ApkBuildMode.INSTANT);
    }

    public boolean generateUniversalApk() {
      if (appBundle.isApex()) {
        return false;
      }
      if (appBundle.isAssetOnly()) {
        return false;
      }
      boolean shouldGenerateAsFallback =
          enableUniversalAsFallbackForSplits && generateSplitApks() && !generateStandaloneApks();
      return apkBuildMode.equals(ApkBuildMode.UNIVERSAL) || shouldGenerateAsFallback;
    }

    public boolean generateSystemApks() {
      if (appBundle.isApex()) {
        return false;
      }
      if (appBundle.isAssetOnly()) {
        return false;
      }
      return apkBuildMode.equals(SYSTEM);
    }

    public boolean generateArchivedApks() {
      if (appBundle.isApex() || appBundle.isAssetOnly()) {
        return false;
      }
      return apkBuildMode.equals(ARCHIVE);
    }

    public boolean generateAssetSlices() {
      if (appBundle.isApex()) {
        return false;
      }
      return appBundle.isAssetOnly()
          || apkBuildMode.equals(ApkBuildMode.DEFAULT)
          || apkBuildMode.equals(ApkBuildMode.INSTANT)
          || apkBuildMode.equals(ApkBuildMode.PERSISTENT);
    }
  }
}
