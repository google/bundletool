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

import static com.android.tools.build.bundletool.model.utils.ModuleDependenciesUtils.getModulesIncludingDependencies;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileDoesNotExist;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.RESOURCES_REFERENCED_IN_MANIFEST_TO_MASTER_SPLIT;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.LocalTestingInfo;
import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.device.IncompatibleDeviceException;
import com.android.tools.build.bundletool.io.ApkPathManager;
import com.android.tools.build.bundletool.io.ApkSerializerManager;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.io.SplitApkSerializer;
import com.android.tools.build.bundletool.io.StandaloneApkSerializer;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.GeneratedAssetSlices;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.OptimizationDimension;
import com.android.tools.build.bundletool.model.SigningConfiguration;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.targeting.AlternativeVariantTargetingPopulator;
import com.android.tools.build.bundletool.model.utils.SplitsXmlInjector;
import com.android.tools.build.bundletool.model.utils.Versions;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.android.tools.build.bundletool.model.version.Version;
import com.android.tools.build.bundletool.optimizations.ApkOptimizations;
import com.android.tools.build.bundletool.optimizations.OptimizationsMerger;
import com.android.tools.build.bundletool.preprocessors.AppBundle64BitNativeLibrariesPreprocessor;
import com.android.tools.build.bundletool.preprocessors.EntryCompressionPreprocessor;
import com.android.tools.build.bundletool.preprocessors.LocalTestingPreprocessor;
import com.android.tools.build.bundletool.splitters.ApkGenerationConfiguration;
import com.android.tools.build.bundletool.splitters.AssetSlicesGenerator;
import com.android.tools.build.bundletool.splitters.ResourceAnalyzer;
import com.android.tools.build.bundletool.splitters.ShardedApksGenerator;
import com.android.tools.build.bundletool.splitters.SplitApksGenerator;
import com.android.tools.build.bundletool.validation.AppBundleValidator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.annotation.CheckReturnValue;

/** Executes the "build-apks" command. */
final class BuildApksManager {

  private static final Logger logger = Logger.getLogger(BuildApksManager.class.getName());

  private final BuildApksCommand command;
  private final Aapt2Command aapt2Command;
  private final Path tempDir;

  BuildApksManager(BuildApksCommand command, Aapt2Command aapt2Command, Path tempDir) {
    this.command = command;
    this.aapt2Command = aapt2Command;
    this.tempDir = tempDir;
  }

  public Path execute() {
    validateInput();

    Path outputDirectory =
        command.getCreateApkSetArchive()
            ? command.getOutputFile().getParent()
            : command.getOutputFile();
    if (outputDirectory != null && Files.notExists(outputDirectory)) {
      logger.info("Output directory '" + outputDirectory + "' does not exist, creating it.");
      FileUtils.createDirectories(outputDirectory);
    }

    // Fail fast with ADB before generating any APKs.
    Optional<DeviceSpec> deviceSpec = command.getDeviceSpec();
    if (command.getGenerateOnlyForConnectedDevice()) {
      deviceSpec = Optional.of(getDeviceSpecFromConnectedDevice());
    }

    try (ZipFile bundleZip = new ZipFile(command.getBundlePath().toFile())) {
      executeWithZip(bundleZip, deviceSpec, command.getSourceStamp());
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "An error occurred when processing the bundle '%s'.", command.getBundlePath()),
          e);
    } finally {
      if (command.isExecutorServiceCreatedByBundleTool()) {
        command.getExecutorService().shutdown();
      }
    }

    return command.getOutputFile();
  }

  private void executeWithZip(
      ZipFile bundleZip, Optional<DeviceSpec> deviceSpec, Optional<SourceStamp> sourceStamp)
      throws IOException {
    Optional<String> stampSource = sourceStamp.map(SourceStamp::getSource);
    AppBundleValidator bundleValidator = AppBundleValidator.create(command.getExtraValidators());

    bundleValidator.validateFile(bundleZip);
    AppBundle appBundle = AppBundle.buildFromZip(bundleZip);
    bundleValidator.validate(appBundle);

    appBundle = applyPreprocessors(appBundle);

    ImmutableSet<BundleModule> requestedModules =
        command.getModules().isEmpty()
            ? ImmutableSet.of()
            : getModulesIncludingDependencies(
                appBundle, getBundleModules(appBundle, command.getModules()));

    BundleConfig bundleConfig = appBundle.getBundleConfig();
    Version bundleVersion = BundleToolVersion.getVersionFromBundleConfig(bundleConfig);

    GeneratedApks.Builder generatedApksBuilder = GeneratedApks.builder();
    GeneratedAssetSlices.Builder generatedAssetSlices = GeneratedAssetSlices.builder();

    boolean enableUniversalAsFallbackForSplits = false;
    ApksToGenerate apksToGenerate =
        new ApksToGenerate(appBundle, command, enableUniversalAsFallbackForSplits, deviceSpec);

    // Split APKs
    if (apksToGenerate.generateSplitApks()) {
      generatedApksBuilder.setSplitApks(generateSplitApks(appBundle, stampSource));
    }

    // Instant APKs
    if (apksToGenerate.generateInstantApks()) {
      generatedApksBuilder.setInstantApks(generateInstantApks(appBundle, stampSource));
    }

    // Standalone APKs
    if (apksToGenerate.generateStandaloneApks()) {
      generatedApksBuilder.setStandaloneApks(
          generateStandaloneApks(tempDir, appBundle, stampSource));
    }

    // Universal APK
    if (apksToGenerate.generateUniversalApk()) {
      // Note: Universal APK is a special type of standalone, with no optimization dimensions.
      ImmutableList<BundleModule> modulesToFuse =
          requestedModules.isEmpty()
              ? modulesToFuse(appBundle.getFeatureModules().values().asList())
              : requestedModules.asList();
      generatedApksBuilder.setStandaloneApks(
          new ShardedApksGenerator(
                  tempDir,
                  bundleVersion,
                  /* strip64BitLibrariesFromShards= */ false,
                  getSuffixStrippings(bundleConfig),
                  stampSource)
              .generateSplits(
                  modulesToFuse,
                  appBundle.getBundleMetadata(),
                  ApkOptimizations.getOptimizationsForUniversalApk()));
    }

    // System APKs
    if (apksToGenerate.generateSystemApks()) {
      generatedApksBuilder.setSystemApks(
          generateSystemApks(appBundle, deviceSpec, requestedModules));
    }

    // Asset Slices
    if (apksToGenerate.generateAssetSlices()) {
      generatedAssetSlices.setAssetSlices(generateAssetSlices(appBundle));
    }

    // Populate alternative targeting based on variant targeting of all APKs.
    GeneratedApks generatedApks =
        AlternativeVariantTargetingPopulator.populateAlternativeVariantTargeting(
            generatedApksBuilder.build(),
            appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion());

    SplitsXmlInjector splitsXmlInjector = new SplitsXmlInjector();
    generatedApks = splitsXmlInjector.process(generatedApks);

    if (deviceSpec.isPresent()) {
      // It is easier to fully check device compatibility once the splits have been generated (in
      // memory). Note that no costly I/O happened up until this point, so it's not too late for
      // this check.
      checkDeviceCompatibilityWithBundle(generatedApks, deviceSpec.get());
    }

    Optional<SigningConfiguration> stampSigningConfiguration =
        sourceStamp.map(SourceStamp::getSigningConfiguration);
    ApkSetBuilder apkSetBuilder =
        createApkSetBuilder(
            aapt2Command,
            command.getSigningConfiguration(),
            stampSigningConfiguration,
            bundleVersion,
            bundleConfig.getCompression(),
            tempDir);

    // Create variants and serialize APKs.
    ApkSerializerManager apkSerializerManager =
        new ApkSerializerManager(
            appBundle,
            apkSetBuilder,
            command.getExecutorService(),
            command.getApkListener().orElse(ApkListener.NO_OP),
            command.getApkModifier().orElse(ApkModifier.NO_OP),
            command.getFirstVariantNumber().orElse(0));

    apkSerializerManager.populateApkSetBuilder(
        generatedApks,
        generatedAssetSlices.build(),
        command.getApkBuildMode(),
        deviceSpec,
        getLocalTestingInfo(appBundle));

    if (command.getOverwriteOutput()) {
      Files.deleteIfExists(command.getOutputFile());
    }
    apkSetBuilder.writeTo(command.getOutputFile());
  }

  private ImmutableList<ModuleSplit> generateStandaloneApks(
      Path tempDir, AppBundle appBundle, Optional<String> stampSource) {
    ImmutableList<BundleModule> allModules = getModulesForStandaloneApks(appBundle);
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ShardedApksGenerator shardedApksGenerator =
        new ShardedApksGenerator(
            tempDir,
            bundleVersion,
            shouldStrip64BitLibrariesFromShards(appBundle),
            getSuffixStrippings(appBundle.getBundleConfig()),
            stampSource);
    return appBundle.isApex()
        ? shardedApksGenerator.generateApexSplits(modulesToFuse(allModules))
        : shardedApksGenerator.generateSplits(
            modulesToFuse(allModules),
            appBundle.getBundleMetadata(),
            getApkOptimizations(appBundle.getBundleConfig()));
  }

  private ImmutableList<ModuleSplit> generateAssetSlices(AppBundle appBundle) {
    ApkGenerationConfiguration assetSlicesGenerationConfiguration =
        getAssetSliceGenerationConfiguration(appBundle.getBundleConfig());
    AssetSlicesGenerator assetSlicesGenerator =
        new AssetSlicesGenerator(appBundle, assetSlicesGenerationConfiguration);
    return assetSlicesGenerator.generateAssetSlices();
  }

  private ImmutableList<ModuleSplit> generateInstantApks(
      AppBundle appBundle, Optional<String> stampSource) {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
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
    return new SplitApksGenerator(
            instantModules, bundleVersion, instantApkGenerationConfiguration, stampSource)
        .generateSplits();
  }

  private ImmutableList<ModuleSplit> generateSplitApks(
      AppBundle appBundle, Optional<String> stampSource) throws IOException {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        getCommonSplitApkGenerationConfiguration(appBundle);
    if (RESOURCES_REFERENCED_IN_MANIFEST_TO_MASTER_SPLIT.enabledForVersion(bundleVersion)) {
      // Make sure that resources reachable from the manifest of the base module will be
      // represented in the master split (by at least one config). This prevents the app
      // from crashing too soon (before reaching Application#onCreate), in case when only
      // the base master split is installed.
      apkGenerationConfiguration.setBaseManifestReachableResources(
          new ResourceAnalyzer(appBundle).findAllAppResourcesReachableFromBaseManifest());
    }

    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    return new SplitApksGenerator(
            featureModules, bundleVersion, apkGenerationConfiguration.build(), stampSource)
        .generateSplits();
  }

  private ImmutableList<ModuleSplit> generateSystemApks(
      AppBundle appBundle,
      Optional<DeviceSpec> deviceSpec,
      ImmutableSet<BundleModule> requestedModules) {
    Version bundleVersion = Version.of(appBundle.getBundleConfig().getBundletool().getVersion());
    ImmutableList<BundleModule> featureModules = appBundle.getFeatureModules().values().asList();
    ImmutableList<BundleModule> modulesToFuse =
        requestedModules.isEmpty() ? modulesToFuse(featureModules) : requestedModules.asList();
    return new ShardedApksGenerator(
            tempDir,
            bundleVersion,
            shouldStrip64BitLibrariesFromShards(appBundle),
            getSuffixStrippings(appBundle.getBundleConfig()))
        .generateSystemSplits(
            /* modules= */ featureModules,
            /* modulesToFuse= */ modulesToFuse.stream()
                .map(BundleModule::getName)
                .collect(toImmutableSet()),
            appBundle.getBundleMetadata(),
            getApkOptimizations(appBundle.getBundleConfig()),
            deviceSpec);
  }

  private static void checkDeviceCompatibilityWithBundle(
      GeneratedApks generatedApks, DeviceSpec deviceSpec) {
    ApkMatcher apkMatcher = new ApkMatcher(deviceSpec);
    generatedApks.getAllApksStream().forEach(apkMatcher::checkCompatibleWithApkTargeting);
  }

  private DeviceSpec getDeviceSpecFromConnectedDevice() {
    AdbServer adbServer = command.getAdbServer().get();
    adbServer.init(command.getAdbPath().get());

    return new DeviceAnalyzer(adbServer).getDeviceSpec(command.getDeviceId());
  }

  private ApkSetBuilder createApkSetBuilder(
      Aapt2Command aapt2Command,
      Optional<SigningConfiguration> signingConfiguration,
      Optional<SigningConfiguration> stampSigningConfiguration,
      Version bundleVersion,
      Compression compression,
      Path tempDir) {
    ApkPathManager apkPathmanager = new ApkPathManager();
    SplitApkSerializer splitApkSerializer =
        new SplitApkSerializer(
            apkPathmanager,
            aapt2Command,
            signingConfiguration,
            stampSigningConfiguration,
            bundleVersion,
            compression);
    StandaloneApkSerializer standaloneApkSerializer =
        new StandaloneApkSerializer(
            apkPathmanager,
            aapt2Command,
            signingConfiguration,
            stampSigningConfiguration,
            bundleVersion,
            compression);

    if (!command.getCreateApkSetArchive()) {
      return ApkSetBuilderFactory.createApkSetWithoutArchiveBuilder(
          splitApkSerializer, standaloneApkSerializer, command.getOutputFile());
    }
    return ApkSetBuilderFactory.createApkSetBuilder(
        splitApkSerializer, standaloneApkSerializer, tempDir);
  }

  private ApkGenerationConfiguration.Builder getCommonSplitApkGenerationConfiguration(
      AppBundle appBundle) {
    BundleConfig bundleConfig = appBundle.getBundleConfig();
    Version bundleToolVersion = Version.of(bundleConfig.getBundletool().getVersion());

    ApkOptimizations apkOptimizations = getApkOptimizations(bundleConfig);

    ApkGenerationConfiguration.Builder apkGenerationConfiguration =
        ApkGenerationConfiguration.builder()
            .setOptimizationDimensions(apkOptimizations.getSplitDimensions());

    boolean enableNativeLibraryCompressionSplitter =
        apkOptimizations.getUncompressNativeLibraries();
    apkGenerationConfiguration.setEnableNativeLibraryCompressionSplitter(
        enableNativeLibraryCompressionSplitter);

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

    apkGenerationConfiguration.setSuffixStrippings(getSuffixStrippings(bundleConfig));

    return apkGenerationConfiguration;
  }

  private static ApkGenerationConfiguration getAssetSliceGenerationConfiguration(
      BundleConfig bundleConfig) {
    ApkOptimizations apkOptimizations = ApkOptimizations.getOptimizationsForAssetSlices();

    return ApkGenerationConfiguration.builder()
        .setOptimizationDimensions(apkOptimizations.getSplitDimensions())
        .setSuffixStrippings(getSuffixStrippings(bundleConfig))
        .build();
  }

  private static ImmutableList<BundleModule> modulesToFuse(ImmutableList<BundleModule> modules) {
    return modules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
  }

  private static ImmutableMap<OptimizationDimension, SuffixStripping> getSuffixStrippings(
      BundleConfig bundleConfig) {
    return OptimizationsMerger.getSuffixStrippings(
        bundleConfig.getOptimizations().getSplitsConfig().getSplitDimensionList());
  }

  private ApkOptimizations getApkOptimizations(BundleConfig bundleConfig) {
    return new OptimizationsMerger()
        .mergeWithDefaults(bundleConfig, command.getOptimizationDimensions());
  }

  private void validateInput() {
    checkFileExistsAndReadable(command.getBundlePath());

    if (command.getCreateApkSetArchive()) {
      if (!command.getOverwriteOutput()) {
        checkFileDoesNotExist(command.getOutputFile());
      }
    }

    if (command.getGenerateOnlyForConnectedDevice()) {
      checkArgument(
          command.getAdbServer().isPresent(),
          "Property 'adbServer' is required when 'generateOnlyForConnectedDevice' is true.");
      checkArgument(
          command.getAdbPath().isPresent(),
          "Property 'adbPath' is required when 'generateOnlyForConnectedDevice' is true.");
      checkFileExistsAndExecutable(command.getAdbPath().get());
    }
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

  private static boolean shouldStrip64BitLibrariesFromShards(AppBundle appBundle) {
    return appBundle
        .getBundleConfig()
        .getOptimizations()
        .getStandaloneConfig()
        .getStrip64BitLibraries();
  }

  @CheckReturnValue
  private AppBundle applyPreprocessors(AppBundle bundle) {
    bundle =
        new AppBundle64BitNativeLibrariesPreprocessor(command.getOutputPrintStream())
            .preprocess(bundle);
    if (command.getLocalTestingMode()) {
      bundle = new LocalTestingPreprocessor().preprocess(bundle);
    }
    bundle = new EntryCompressionPreprocessor().preprocess(bundle);
    return bundle;
  }

  private static LocalTestingInfo getLocalTestingInfo(AppBundle bundle) {
    LocalTestingInfo.Builder localTestingInfo = LocalTestingInfo.newBuilder();
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
    private final boolean forceGenerateStandaloneApks;

    private ApksToGenerate(
        AppBundle appBundle,
        BuildApksCommand command,
        boolean enableUniversalAsFallbackForSplits,
        Optional<DeviceSpec> deviceSpec) {
      this.appBundle = appBundle;
      this.apkBuildMode = command.getApkBuildMode();
      this.enableUniversalAsFallbackForSplits = enableUniversalAsFallbackForSplits;
      this.deviceSpec = deviceSpec;
      this.forceGenerateStandaloneApks = !command.getOptimizationDimensions().isEmpty();
      validate();
    }

    private void validate() {
      if (appBundle.isApex() && apkBuildMode.equals(ApkBuildMode.UNIVERSAL)) {
        throw new CommandExecutionException("APEX bundles do not support universal apks.");
      }

      if (deviceSpec.isPresent()) {
        int deviceSdk = deviceSpec.get().getSdkVersion();
        Optional<Integer> appMaxSdk =
            appBundle.getBaseModule().getAndroidManifest().getMaxSdkVersion();

        if ((apkBuildMode.equals(ApkBuildMode.DEFAULT)
            || apkBuildMode.equals(ApkBuildMode.PERSISTENT))) {
          if (deviceSdk >= Versions.ANDROID_L_API_VERSION) {
            if (!generateSplitApks()) {
              throw new IncompatibleDeviceException(
                  "App Bundle targets pre-L devices, but the device has SDK version higher "
                      + "or equal to L.");
            }
          } else {
            if (!generateStandaloneApks()) {
              throw new IncompatibleDeviceException(
                  "App Bundle targets L+ devices, but the device has SDK version lower than L.");
            }
          }
        }

        if (appMaxSdk.isPresent() && deviceSdk > appMaxSdk.get()) {
          throw new IncompatibleDeviceException(
              "Max SDK version of the App Bundle is lower than SDK version of the device");
        }
      }

      checkState(
          generateStandaloneApks()
              || generateSplitApks()
              || generateInstantApks()
              || generateUniversalApk()
              || generateSystemApks(),
          "No APKs to generate.");
    }

    public boolean generateSplitApks() {
      if (appBundle.isApex()) {
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
      if (appBundle.isApex() || forceGenerateStandaloneApks) {
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
      return apkBuildMode.equals(ApkBuildMode.DEFAULT) || apkBuildMode.equals(ApkBuildMode.INSTANT);
    }

    public boolean generateUniversalApk() {
      if (appBundle.isApex()) {
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
      return apkBuildMode.isAnySystemMode();
    }

    public boolean generateAssetSlices() {
      if (appBundle.isApex()) {
        return false;
      }
      return apkBuildMode.equals(ApkBuildMode.DEFAULT)
          || apkBuildMode.equals(ApkBuildMode.INSTANT)
          || apkBuildMode.equals(ApkBuildMode.PERSISTENT);
    }
  }
}
